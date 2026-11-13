package dev.villagerreroller.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.villagerreroller.logic.RerollerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves the settings to config/villagerreroller.json and reads them back. */
public final class ConfigIO {

    private static final Logger LOGGER = LogManager.getLogger("villagerreroller");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigIO() {
    }

    /** The game's config folder; set by the loader's entry class before anything is loaded. */
    public static Path configDir = java.nio.file.Paths.get("config");

    /** The game folder (where servers.dat lives): the config folder's parent on every loader. */
    public static Path gameDir() {
        Path parent = configDir.toAbsolutePath().getParent();
        return parent != null ? parent : configDir.toAbsolutePath();
    }

    public static Path path() {
        return configDir.resolve("villagerreroller.json");
    }

    public static RerollerConfig load() {
        Path p = path();
        RerollerConfig cfg = null;
        if (Files.isRegularFile(p)) {
            try {
                String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                cfg = GSON.fromJson(json, RerollerConfig.class);
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Could not read {} - using defaults. ({})", p, e.toString());
            }
        }
        if (cfg == null) {
            cfg = new RerollerConfig();
        }
        cfg.sanitize();
        return cfg;
    }

    public static void save(RerollerConfig cfg) {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, GSON.toJson(cfg).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Could not save {} ({})", p, e.toString());
        }
    }
}
