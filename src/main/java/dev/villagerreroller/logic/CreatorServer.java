package dev.villagerreroller.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod maker's own server, Logic Survival. It is put at the end of the Multiplayer list once, the first time the
 * mod starts (the mod pages say so). If the player deletes it, it never comes back by itself; the Server button in
 * the menu adds it again.
 */
public final class CreatorServer {
    private CreatorServer() {
    }

    public static final String NAME = "Logic Survival";
    public static final String IP = "play.logicsurvival.fyi";

    private static Path file(Path gameDir) {
        return gameDir.resolve("servers.dat");
    }

    /** First start of this mod (or of a version that has this): add it once. @return true if the config changed. */
    public static boolean firstStart(RerollerConfig cfg, Path gameDir) {
        if (cfg.serverOffered) {
            return false;
        }
        cfg.serverOffered = true;
        System.out.println("[Villager Reroller] Logic Survival server list: " + add(gameDir));
        return true;
    }

    /** Already in the player's multiplayer list? */
    public static boolean added(Path gameDir) {
        try {
            Path f = file(gameDir);
            return Files.exists(f) && ServersDat.has(Files.readAllBytes(f), IP);
        } catch (Exception e) {
            return false;
        }
    }

    /** Adds it to the end of the multiplayer list. Returns a short message for the player. */
    public static String add(Path gameDir) {
        try {
            Path f = file(gameDir);
            byte[] old = Files.exists(f) ? Files.readAllBytes(f) : null;
            if (old != null && ServersDat.has(old, IP)) {
                return "Logic Survival is already in your server list.";
            }
            byte[] now = ServersDat.withServer(old, NAME, IP);
            Path tmp = f.resolveSibling("servers.dat.vrtmp");
            Files.write(tmp, now);
            if (old != null) {
                Files.write(f.resolveSibling("servers.dat_old"), old);   // the game's own backup name
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return "Added! Open Multiplayer to join Logic Survival.";
        } catch (Exception e) {
            return "Couldn't add it - add " + IP + " in Multiplayer > Add Server.";
        }
    }
}
