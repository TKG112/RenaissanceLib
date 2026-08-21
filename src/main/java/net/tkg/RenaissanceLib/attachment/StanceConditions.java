package net.tkg.RenaissanceLib.attachment;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import java.util.List;
import java.util.Locale;

/**
 * Evaluates the {@code require} conditions a state can declare, e.g. a bipod whose recoil
 * bonus only applies while prone.
 *
 * <p>Conditions are checked against vanilla, cross-side-synced state (pose, crouch) so the
 * same answer is reached on the client (which applies recoil) and the server (which applies
 * spread) — the property cache is shared between them.
 */
public final class StanceConditions {
    public static final String PRONE  = "prone";
    public static final String CROUCH = "crouch";

    private StanceConditions() {}

    /**
     * True if {@code entity} satisfies at least one of the listed conditions. An empty list
     * means "no requirement", which is always satisfied.
     */
    public static boolean anyMet(LivingEntity entity, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return true;
        if (entity == null) return false;
        for (String condition : conditions) {
            if (isMet(entity, condition)) return true;
        }
        return false;
    }

    private static boolean isMet(LivingEntity entity, String condition) {
        switch (condition.toLowerCase(Locale.ROOT)) {
            case PRONE:
                // TaC:Z's crawl forces Pose.SWIMMING while on the ground and out of water,
                // so pose == SWIMMING with no water present distinguishes prone from an
                // actual swim.
                return entity.getPose() == Pose.SWIMMING && !entity.isInWater();
            case CROUCH:
                return entity.isCrouching();
            default:
                return false;
        }
    }
}
