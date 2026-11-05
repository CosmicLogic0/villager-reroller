package dev.villagerreroller.logic;

/**
 * Works out which movement keys to hold to walk towards a point WITHOUT turning the camera:
 * the direction to the target is split into the player's own forward/sideways axes.
 */
public final class WalkMath {

    private WalkMath() {
    }

    /** Keys to hold this tick. */
    public record Keys(boolean forward, boolean back, boolean left, boolean right, boolean sneak, double distance) {
        public boolean any() {
            return forward || back || left || right;
        }
    }

    /**
     * @param yawDegrees the player's yaw (Minecraft convention: 0 = facing +Z / south, 90 = facing -X / west)
     * @param deadZone   how close (blocks) an axis has to be before that key is released
     * @param sneakBelow distance (blocks) under which to sneak for precise stopping
     */
    public static Keys keysTowards(double px, double pz, double tx, double tz, float yawDegrees,
                                   double deadZone, double sneakBelow) {
        double dx = tx - px;
        double dz = tz - pz;
        double yaw = Math.toRadians(yawDegrees);
        // forward vector (-sin, cos); right-hand vector (-cos, -sin)
        double fwd = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new Keys(fwd > deadZone, fwd < -deadZone, right < -deadZone, right > deadZone, dist < sneakBelow, dist);
    }
}
