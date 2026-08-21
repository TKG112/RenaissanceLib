package net.tkg.RenaissanceLib.client;

import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The gun model currently being rendered, published by {@code BedrockGunModelMixin} for the duration of
 * its {@code render}.
 *
 * <p>Used to <em>hoist</em> a rail-mounted laser's draw onto the gun model's own delegate queue, so it
 * renders in the same pass as a native-slot attachment. That matters for the scope's ocular clip: TaC:Z
 * enables the ocular stencil test during the <em>gun's</em> {@code super.render}, so anything drawn there
 * (the gun body, native-slot lasers) is masked out of the lens. Our rail mounts normally render nested
 * inside the scope model's delegate — one pass earlier, before that stencil test exists — so a rail
 * laser's beam bleeds into the ocular. Hoisting a non-optic mount here makes its beam get clipped out of
 * the lens like a native laser.
 *
 * <p>Client render thread only; a plain static suffices.
 */
@OnlyIn(Dist.CLIENT)
public final class RailGunModelContext {
    private RailGunModelContext() {}

    @Nullable
    private static BedrockGunModel current;

    public static void begin(BedrockGunModel model) {
        current = model;
    }

    public static void end() {
        current = null;
    }

    @Nullable
    public static BedrockGunModel current() {
        return current;
    }
}
