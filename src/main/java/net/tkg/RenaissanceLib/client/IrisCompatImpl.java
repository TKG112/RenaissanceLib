package net.tkg.RenaissanceLib.client;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The only class in the mod that references {@code IrisApi}. It is loaded lazily — {@link IrisCompat}
 * calls it only after confirming Oculus is present — so its {@code IrisApi} reference is never resolved
 * on installs without Oculus. Do not reference this class except through {@link IrisCompat}.
 */
@OnlyIn(Dist.CLIENT)
final class IrisCompatImpl {
    private IrisCompatImpl() {}

    static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
