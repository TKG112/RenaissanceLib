package net.tkg.RenaissanceLib.client.underbarrel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Transient client-side timers for the underbarrel's fire effects (muzzle flash for now; shell later). Set
 * when the local player fires the underbarrel and read by the effect renderers. Global (not per-entity), so
 * for now it only drives the shooter's own view — other players seeing a shooter's underbarrel flash would
 * need a synced fire event (a later addition).
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelEffects {
    /** How long the muzzle flash stays visible after a shot, in milliseconds. */
    private static final long FLASH_WINDOW_MS = 50L;

    // Not MIN_VALUE: (now - MIN_VALUE) overflows and would read as "flashing" forever until the first shot.
    private static long lastFlashMs = 0L;

    private UnderbarrelEffects() {}

    /** Mark that the underbarrel just fired (starts the muzzle flash). */
    public static void onFire() {
        lastFlashMs = System.currentTimeMillis();
    }

    /** Whether the muzzle flash should currently be drawn. */
    public static boolean isFlashing() {
        return System.currentTimeMillis() - lastFlashMs < FLASH_WINDOW_MS;
    }
}
