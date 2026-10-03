package dev.carbon.client.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.core.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Schema-versioned local JSON profiles with debounced, atomic background saves. */
public final class ConfigManager implements AutoCloseable {
    public static final int SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final String DEFAULT_PROFILE = "default";
    private static final long SAVE_DELAY_MILLIS = 350L;
    private static final Pattern PROFILE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configFile;
    private final Path profilesDirectory;
    private final ModuleManager modules;
    private final ScheduledExecutorService writer;
    private final Object saveLock = new Object();
    private final Map<String, JsonObject> namedProfiles = new HashMap<>();

    private JsonObject defaultModules = new JsonObject();
    private String activeProfile = DEFAULT_PROFILE;
    private Map<Path, String> lastWritten = Map.of();
    private Snapshot pending;
    private Snapshot inFlight;
    private ScheduledFuture<?> scheduledSave;
    private volatile boolean loading;
    private volatile boolean closed;

    public ConfigManager(Path directory, ModuleManager modules) {
        Objects.requireNonNull(directory, "directory");
        this.configFile = directory.resolve("config.json");
        this.profilesDirectory = directory.resolve("profiles");
        this.modules = Objects.requireNonNull(modules, "modules");
        this.writer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Carbon Client config writer");
            thread.setDaemon(true);
            return thread;
        });
        modules.setChangeListener(this::markDirty);
    }

    public void load() {
        loading = true;
        try {
            JsonObject root = readDocument(configFile, true);
            if (root != null && root.has("modules") && root.get("modules").isJsonObject()) {
                defaultModules = root.getAsJsonObject("modules").deepCopy();
            } else {
                defaultModules = new JsonObject();
            }

            activeProfile = readProfileName(root);
            JsonObject state = modulesFor(activeProfile);
            if (state == null) {
                activeProfile = DEFAULT_PROFILE;
                state = defaultModules;
            }
            applyModuleState(state);
        } finally {
            loading = false;
        }

        captureActiveProfile();
        synchronized (saveLock) {
            lastWritten = snapshotFiles();
        }
    }

    public String activeProfile() {
        return activeProfile;
    }

    public List<String> listProfiles() {
        ArrayList<String> result = new ArrayList<>();
        result.add(DEFAULT_PROFILE);
        for (String profileName : namedProfiles.keySet()) {
            if (!result.contains(profileName)) {
                result.add(profileName);
            }
        }
        if (Files.isDirectory(profilesDirectory)) {
            try (var paths = Files.list(profilesDirectory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .map(name -> name.substring(0, name.length() - 5))
                        .filter(ConfigManager::validProfileName)
                        .filter(name -> !result.contains(name))
                        .forEach(result::add);
            } catch (IOException failure) {
                LOGGER.warn("Could not list Carbon Client profiles in {}", profilesDirectory, failure);
            }
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    /** Saves the outgoing profile in memory and activates a local profile by safe slug. */
    public boolean switchProfile(String profileName) {
        if (!validProfileName(profileName) || closed) {
            return false;
        }
        if (activeProfile.equals(profileName)) {
            return true;
        }

        loading = true;
        JsonObject nextState;
        try {
            captureActiveProfile();
            activeProfile = profileName;
            nextState = modulesFor(profileName);
            if (nextState == null) {
                nextState = defaultModules.deepCopy();
                if (!DEFAULT_PROFILE.equals(profileName)) {
                    namedProfiles.put(profileName, nextState.deepCopy());
                }
            }
            applyModuleState(nextState);
        } finally {
            loading = false;
        }
        markDirty();
        return true;
    }

    /** Creates and activates a named profile containing the current module state. */
    public boolean createProfile(String profileName) {
        if (!validProfileName(profileName) || DEFAULT_PROFILE.equals(profileName) || closed
                || namedProfiles.containsKey(profileName) || Files.exists(profilePath(profileName))) {
            return false;
        }
        captureActiveProfile();
        namedProfiles.put(profileName, captureModuleState());
        activeProfile = profileName;
        markDirty();
        return true;
    }

    private JsonObject modulesFor(String profileName) {
        if (DEFAULT_PROFILE.equals(profileName)) {
            return defaultModules;
        }
        JsonObject cached = namedProfiles.get(profileName);
        if (cached != null) {
            return cached;
        }
        JsonObject document = readDocument(profilePath(profileName), true);
        if (document == null || !document.has("modules") || !document.get("modules").isJsonObject()) {
            return null;
        }
        JsonObject state = document.getAsJsonObject("modules").deepCopy();
        namedProfiles.put(profileName, state);
        return state;
    }

    private void captureActiveProfile() {
        JsonObject state = captureModuleState();
        if (DEFAULT_PROFILE.equals(activeProfile)) {
            defaultModules = state;
        } else {
            namedProfiles.put(activeProfile, state);
        }
    }

    private void applyModuleState(JsonObject state) {
        for (Module module : modules.modules()) {
            JsonElement entry = state.get(module.id());
            if (entry == null || !entry.isJsonObject()) {
                module.setEnabled(module.defaultEnabled());
                continue;
            }

            JsonObject moduleState = entry.getAsJsonObject();
            JsonElement settingsElement = moduleState.get("settings");
            if (settingsElement != null && settingsElement.isJsonObject()) {
                JsonObject settings = settingsElement.getAsJsonObject();
                for (Setting<?> setting : module.settings()) {
                    JsonElement value = settings.get(setting.id());
                    if (value != null) {
                        try {
                            setting.fromJson(value);
                        } catch (RuntimeException failure) {
                            LOGGER.warn("Ignoring invalid setting '{}.{}' in Carbon Client profile",
                                    module.id(), setting.id(), failure);
                        }
                    }
                }
            }

            boolean enabled = module.defaultEnabled();
            JsonElement enabledElement = moduleState.get("enabled");
            if (enabledElement != null && enabledElement.isJsonPrimitive()
                    && enabledElement.getAsJsonPrimitive().isBoolean()) {
                enabled = enabledElement.getAsBoolean();
            }
            module.setEnabled(enabled);
        }
    }

    private JsonObject captureModuleState() {
        JsonObject result = new JsonObject();
        for (Module module : modules.modules()) {
            JsonObject moduleState = new JsonObject();
            moduleState.addProperty("enabled", module.enabled());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.settings()) {
                settings.add(setting.id(), setting.toJson());
            }
            moduleState.add("settings", settings);
            result.add(module.id(), moduleState);
        }
        return result;
    }

    private JsonObject readDocument(Path file, boolean preserveBroken) {
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Expected a JSON object");
            }
            JsonObject document = parsed.getAsJsonObject();
            if (!document.has("schemaVersion") || !document.get("schemaVersion").isJsonPrimitive()
                    || document.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported or missing config schema version");
            }
            if (!document.has("modules") || !document.get("modules").isJsonObject()) {
                throw new IllegalArgumentException("Missing module state object");
            }
            return document;
        } catch (Exception failure) {
            LOGGER.warn("Could not read Carbon Client config {}; defaults will be used", file, failure);
            if (preserveBroken) {
                preserveBrokenFile(file);
            }
            return null;
        }
    }

    private String readProfileName(JsonObject root) {
        if (root == null) {
            return DEFAULT_PROFILE;
        }
        JsonElement profile = root.get("activeProfile");
        if (profile == null || !profile.isJsonPrimitive() || !profile.getAsJsonPrimitive().isString()) {
            return DEFAULT_PROFILE;
        }
        String name = profile.getAsString();
        return validProfileName(name) ? name : DEFAULT_PROFILE;
    }

    private void markDirty() {
        if (loading || closed) {
            return;
        }
        Map<Path, String> files;
        try {
            captureActiveProfile();
            files = snapshotFiles();
        } catch (RuntimeException failure) {
            LOGGER.error("Could not serialize changed Carbon Client settings", failure);
            return;
        }
        synchronized (saveLock) {
            boolean matchesLast = files.equals(lastWritten);
            boolean matchesInFlight = inFlight != null && files.equals(inFlight.files());
            if (matchesLast && (inFlight == null || matchesInFlight)) {
                pending = null;
                cancelScheduledSave();
                return;
            }
            if (matchesInFlight) {
                pending = null;
                cancelScheduledSave();
                return;
            }
            if (pending != null && files.equals(pending.files())) {
                if (scheduledSave == null && !closed) {
                    scheduleSave(SAVE_DELAY_MILLIS);
                }
                return;
            }
            pending = new Snapshot(files);
            scheduleSave(SAVE_DELAY_MILLIS);
        }
    }

    private Map<Path, String> snapshotFiles() {
        LinkedHashMap<Path, String> files = new LinkedHashMap<>();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("activeProfile", activeProfile);
        root.add("modules", defaultModules.deepCopy());
        files.put(configFile, GSON.toJson(root));

        for (Map.Entry<String, JsonObject> entry : namedProfiles.entrySet()) {
            JsonObject document = new JsonObject();
            document.addProperty("schemaVersion", SCHEMA_VERSION);
            document.add("modules", entry.getValue().deepCopy());
            files.put(profilePath(entry.getKey()), GSON.toJson(document));
        }
        return files;
    }

    private void scheduleSave(long delayMillis) {
        cancelScheduledSave();
        scheduledSave = writer.schedule(this::flushPending, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduledSave() {
        if (scheduledSave != null) {
            scheduledSave.cancel(false);
            scheduledSave = null;
        }
    }

    private void flushPending() {
        Snapshot snapshot;
        synchronized (saveLock) {
            snapshot = pending;
            pending = null;
            scheduledSave = null;
            if (snapshot == null) {
                return;
            }
            inFlight = snapshot;
        }

        try {
            writeAtomically(snapshot.files());
            synchronized (saveLock) {
                lastWritten = snapshot.files();
                inFlight = null;
                if (pending != null) {
                    if (pending.files().equals(lastWritten)) {
                        pending = null;
                    } else if (!closed) {
                        scheduleSave(0L);
                    }
                }
            }
        } catch (IOException failure) {
            LOGGER.error("Could not save Carbon Client config", failure);
            synchronized (saveLock) {
                inFlight = null;
                if (pending == null) {
                    pending = snapshot;
                }
            }
        }
    }

    private void writeAtomically(Map<Path, String> files) throws IOException {
        for (Map.Entry<Path, String> entry : files.entrySet()) {
            Path target = entry.getKey();
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer output = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                output.write(entry.getValue());
                output.flush();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void preserveBrokenFile(Path file) {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
            int suffix = 1;
            while (Files.exists(backup)) {
                backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis() + "-" + suffix++);
            }
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException backupFailure) {
            LOGGER.warn("Could not preserve invalid Carbon Client config {}", file, backupFailure);
        }
    }

    private Path profilePath(String profileName) {
        return profilesDirectory.resolve(profileName + ".json");
    }

    private static boolean validProfileName(String name) {
        return name != null && (DEFAULT_PROFILE.equals(name) || PROFILE_PATTERN.matcher(name).matches());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        markDirty();
        synchronized (saveLock) {
            cancelScheduledSave();
            if (pending != null) {
                scheduleSave(0L);
            }
            closed = true;
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                writer.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }

        Snapshot remaining;
        synchronized (saveLock) {
            remaining = pending;
            pending = null;
        }
        if (remaining != null) {
            try {
                writeAtomically(remaining.files());
                synchronized (saveLock) {
                    lastWritten = remaining.files();
                }
            } catch (IOException failure) {
                LOGGER.error("Could not flush Carbon Client config while stopping", failure);
            }
        }
    }

    private record Snapshot(Map<Path, String> files) {
    }
}
