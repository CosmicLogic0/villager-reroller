package dev.villagerreroller.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.villagerreroller.logic.RerollerConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads and writes config/villagerreroller.json. */
public final class ConfigIO {

    private static final Logger LOGGER = LoggerFactory.getLogger("villagerreroller");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigIO() {
    }

    /** The game folder (where servers.dat lives). */
    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("villagerreroller.json");
    }

    public static RerollerConfig load() {
        Path p = path();
        RerollerConfig cfg = null;
        if (Files.isRegularFile(p)) {
            try {
                String json = Files.readString(p, StandardCharsets.UTF_8);
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
            Files.writeString(p, GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not save {} ({})", p, e.toString());
        }
    }
}
