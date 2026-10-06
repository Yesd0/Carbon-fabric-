package dev.carbon.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Local, client-only waypoint storage and an opt-in nearest-waypoint HUD readout. */
public final class WaypointManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_HUD_ROWS = 5;
    private static final int HUD_GREEN = 0xFF39E18E;
    private static final int HUD_WHITE = 0xFFF4F6F3;
    private static final int HUD_BLACK = 0xD9080A0B;

    private final Path file;
    private final ArrayList<Waypoint> waypoints = new ArrayList<>();
    private final Waypoint[] nearest = new Waypoint[MAX_HUD_ROWS];
    private final double[] nearestDistances = new double[MAX_HUD_ROWS];
    private final EventBus.Subscription<HudRenderEvent> hudSubscription;
    private boolean showOnHud;
    private boolean closed;

    public WaypointManager(Path file, EventBus eventBus) {
        this.file = Objects.requireNonNull(file, "file");
        load();
        this.hudSubscription = Objects.requireNonNull(eventBus, "eventBus")
                .subscribe(HudRenderEvent.class, this::renderHud);
    }

    public List<Waypoint> waypoints() {
        return List.copyOf(waypoints);
    }

    public boolean showOnHud() {
        return showOnHud;
    }

    public void setShowOnHud(boolean showOnHud) {
        if (this.showOnHud == showOnHud) {
            return;
        }
        this.showOnHud = showOnHud;
        save();
    }

    public Waypoint createAtPlayer(String requestedName) {
        if (closed) {
            throw new IllegalStateException("Waypoint manager is closed");
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null) {
            return null;
        }
        String name = normalizeName(requestedName);
        if (name.isEmpty()) {
            return null;
        }
        BlockPos position = player.blockPosition();
        String dimension = player.level().dimension().identifier().toString();
        Waypoint waypoint = new Waypoint(UUID.randomUUID().toString(), name, dimension,
                position.getX(), position.getY(), position.getZ());
        waypoints.add(waypoint);
        save();
        return waypoint;
    }

    public boolean delete(String id) {
        if (id == null || closed) {
            return false;
        }
        boolean removed = waypoints.removeIf(waypoint -> waypoint.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public String currentDimension() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return "";
        }
        return client.player.level().dimension().identifier().toString();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Expected a waypoint document object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("schemaVersion") && root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported waypoint schema version");
            }
            showOnHud = root.has("showOnHud") && root.get("showOnHud").getAsBoolean();
            JsonElement waypointsValue = root.get("waypoints");
            if (waypointsValue != null && waypointsValue.isJsonArray()) {
                for (JsonElement entry : waypointsValue.getAsJsonArray()) {
                    if (!entry.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = entry.getAsJsonObject();
                    String id = stringValue(item, "id");
                    String name = normalizeName(stringValue(item, "name"));
                    String dimension = stringValue(item, "dimension");
                    if (id.isBlank() || name.isBlank() || dimension.isBlank()
                            || !item.has("x") || !item.has("y") || !item.has("z")) {
                        continue;
                    }
                    waypoints.add(new Waypoint(id, name, dimension,
                            item.get("x").getAsInt(), item.get("y").getAsInt(), item.get("z").getAsInt()));
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not load Carbon waypoints from {}; preserving defaults", file, failure);
            preserveBrokenFile();
            waypoints.clear();
            showOnHud = false;
        }
    }

    private void save() {
        if (closed) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("showOnHud", showOnHud);
        JsonArray entries = new JsonArray();
        for (Waypoint waypoint : waypoints) {
            JsonObject item = new JsonObject();
            item.addProperty("id", waypoint.id());
            item.addProperty("name", waypoint.name());
            item.addProperty("dimension", waypoint.dimension());
            item.addProperty("x", waypoint.x());
            item.addProperty("y", waypoint.y());
            item.addProperty("z", waypoint.z());
            entries.add(item);
        }
        root.add("waypoints", entries);
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
            LOGGER.error("Could not save Carbon waypoints to {}", file, failure);
        }
    }

    private void preserveBrokenFile() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            LOGGER.warn("Could not preserve invalid Carbon waypoint file {}", file, failure);
        }
    }

    private void renderHud(HudRenderEvent event) {
        if (!showOnHud) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null) {
            return;
        }
        String dimension = player.level().dimension().identifier().toString();
        for (int index = 0; index < MAX_HUD_ROWS; index++) {
            nearest[index] = null;
            nearestDistances[index] = Double.POSITIVE_INFINITY;
        }
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        for (Waypoint waypoint : waypoints) {
            if (!waypoint.dimension().equals(dimension)) {
                continue;
            }
            double dx = waypoint.x() + 0.5 - playerX;
            double dy = waypoint.y() - playerY;
            double dz = waypoint.z() + 0.5 - playerZ;
            double squaredDistance = dx * dx + dy * dy + dz * dz;
            int insert = MAX_HUD_ROWS - 1;
            if (squaredDistance >= nearestDistances[insert]) {
                continue;
            }
            while (insert > 0 && squaredDistance < nearestDistances[insert - 1]) {
                nearest[insert] = nearest[insert - 1];
                nearestDistances[insert] = nearestDistances[insert - 1];
                insert--;
            }
            nearest[insert] = waypoint;
            nearestDistances[insert] = squaredDistance;
        }

        int present = 0;
        for (Waypoint waypoint : nearest) {
            if (waypoint != null) {
                present++;
            }
        }
        if (present == 0) {
            return;
        }
        Font font = client.font;
        GuiGraphicsExtractor graphics = event.graphics();
        int lineHeight = font.lineHeight + 5;
        int widest = font.width("WAYPOINTS");
        for (Waypoint waypoint : nearest) {
            if (waypoint == null) {
                continue;
            }
            int distance = (int) Math.round(Math.sqrt(distanceSquared(waypoint, playerX, playerY, playerZ)));
            String line = waypoint.name() + "  " + distance + "m";
            widest = Math.max(widest, font.width(line));
        }
        int boxWidth = widest + 28;
        int boxHeight = (present + 1) * lineHeight + 12;
        int left = Math.max(8, event.width() - boxWidth - 8);
        int top = 10;
        graphics.fill(left, top, left + boxWidth, top + boxHeight, HUD_BLACK);
        graphics.fill(left, top, left + 2, top + boxHeight, HUD_GREEN);
        graphics.text(font, "WAYPOINTS", left + 10, top + 6, HUD_GREEN, false);
        int row = 1;
        for (Waypoint waypoint : nearest) {
            if (waypoint == null) {
                continue;
            }
            int distance = (int) Math.round(Math.sqrt(distanceSquared(waypoint, playerX, playerY, playerZ)));
            graphics.text(font, waypoint.name() + "  " + distance + "m",
                    left + 10, top + 6 + row * lineHeight, HUD_WHITE, false);
            row++;
        }
    }

    private static double distanceSquared(Waypoint waypoint, double x, double y, double z) {
        double dx = waypoint.x() + 0.5 - x;
        double dy = waypoint.y() - y;
        double dz = waypoint.z() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.strip().replaceAll("\\s+", " ");
        return normalized.length() > MAX_NAME_LENGTH
                ? normalized.substring(0, MAX_NAME_LENGTH).stripTrailing() : normalized;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        save();
        closed = true;
        hudSubscription.close();
    }

    public record Waypoint(String id, String name, String dimension, int x, int y, int z) {
    }
}
