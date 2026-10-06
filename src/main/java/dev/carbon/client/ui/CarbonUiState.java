package dev.carbon.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Persisted presentation state for the Carbon Mods screen, independent from module profiles. */
public final class CarbonUiState {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern MODULE_ID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_QUERY_LENGTH = 64;

    private final Path file;
    private final LinkedHashSet<String> pinnedModules = new LinkedHashSet<>();
    private Filter filter = Filter.ALL;
    private ViewMode viewMode = ViewMode.GRID;
    private SortMode sortMode = SortMode.NAME;
    private boolean descending;
    private String query = "";

    private CarbonUiState(Path file) {
        this.file = file;
    }

    public static CarbonUiState load(Path file) {
        CarbonUiState state = new CarbonUiState(file);
        if (!Files.isRegularFile(file)) {
            return state;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Expected a UI-state object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("schemaVersion") && root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Carbon UI-state schema version");
            }
            state.filter = enumValue(root, "filter", Filter.class, Filter.ALL);
            state.viewMode = enumValue(root, "view", ViewMode.class, ViewMode.GRID);
            state.sortMode = enumValue(root, "sort", SortMode.class, SortMode.NAME);
            state.descending = root.has("descending") && root.get("descending").getAsBoolean();
            if (root.has("query") && root.get("query").isJsonPrimitive()) {
                state.query = boundedQuery(root.get("query").getAsString());
            }
            JsonElement pinsElement = root.get("pinnedModules");
            if (pinsElement != null && pinsElement.isJsonArray()) {
                JsonArray pins = pinsElement.getAsJsonArray();
                for (JsonElement pin : pins) {
                    if (pin.isJsonPrimitive() && pin.getAsJsonPrimitive().isString()) {
                        String id = pin.getAsString();
                        if (MODULE_ID.matcher(id).matches()) {
                            state.pinnedModules.add(id);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not load Carbon UI state from {}; using defaults", file, failure);
        }
        return state;
    }

    public Filter filter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter == null ? Filter.ALL : filter;
        save();
    }

    public ViewMode viewMode() {
        return viewMode;
    }

    public void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode == null ? ViewMode.GRID : viewMode;
        save();
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode == null ? SortMode.NAME : sortMode;
        save();
    }

    public boolean descending() {
        return descending;
    }

    public void setDescending(boolean descending) {
        this.descending = descending;
        save();
    }

    public String query() {
        return query;
    }

    /** Query saves are deferred until screen close to avoid synchronous disk writes per keystroke. */
    public void setQuery(String query) {
        this.query = boundedQuery(query);
    }

    public boolean isPinned(String moduleId) {
        return pinnedModules.contains(moduleId);
    }

    public void togglePinned(String moduleId) {
        if (moduleId == null || !MODULE_ID.matcher(moduleId).matches()) {
            return;
        }
        if (!pinnedModules.add(moduleId)) {
            pinnedModules.remove(moduleId);
        }
        save();
    }

    public Set<String> pinnedModules() {
        return Set.copyOf(pinnedModules);
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("filter", filter.name());
        root.addProperty("view", viewMode.name());
        root.addProperty("sort", sortMode.name());
        root.addProperty("descending", descending);
        root.addProperty("query", query);
        JsonArray pins = new JsonArray();
        for (String id : pinnedModules) {
            pins.add(id);
        }
        root.add("pinnedModules", pins);

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not save Carbon UI state to {}", file, failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonObject root, String key, Class<E> type, E fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, root.get(key).getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            return fallback;
        }
    }

    private static String boundedQuery(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String bounded = value.length() > MAX_QUERY_LENGTH ? value.substring(0, MAX_QUERY_LENGTH) : value;
        return bounded.strip();
    }

    public enum Filter {
        ALL("All modules"),
        HUD("HUD"),
        VISUAL("Visual"),
        UTILITY("Utility"),
        PERFORMANCE("Performance"),
        PINNED("Pinned"),
        ENABLED("Enabled");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ViewMode {
        GRID,
        LIST
    }

    public enum SortMode {
        NAME("Name"),
        CATEGORY("Category"),
        ENABLED("Status");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
