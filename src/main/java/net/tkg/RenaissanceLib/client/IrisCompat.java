package net.tkg.RenaissanceLib.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

/**
 * Safe, optional access to Iris/Oculus. The mod's scope-shader feature only needs to know whether an
 * Iris shaderpack is currently rendering; everything that actually touches {@code IrisApi} lives in
 * {@link IrisCompatImpl}, which is <em>only class-loaded when Oculus is installed</em> (guarded by the
 * short-circuit below). Without Oculus, {@code IrisApi} is never referenced, so the mod loads and runs
 * normally instead of crashing with {@code ClassMetadataNotFoundException}/{@code NoClassDefFoundError}.
 */
@OnlyIn(Dist.CLIENT)
public final class IrisCompat {
    private static final boolean LOADED = ModList.get().isLoaded("oculus");

    private IrisCompat() {}

    /** Whether an Iris/Oculus shaderpack is currently in use; always {@code false} when Oculus is absent. */
    public static boolean isShaderPackInUse() {
        return LOADED && IrisCompatImpl.isShaderPackInUse();
    }
}
