package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.client.animation.screen.RefitTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceConfig;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.client.IrisCompat;
import net.tkg.RenaissanceLib.mixin.client.PostChainAccessor;
import org.lwjgl.opengl.GL11;

/**
 * Blurs the world behind the interactive refit screen while the gun stays sharp: vanilla's two-pass blur post
 * chain runs on the main target after the world is drawn and <em>before</em> the first-person pass
 * ({@code GameRendererMixin}, at {@code renderItemInHand} HEAD) — the refit gun is the first-person model, drawn
 * next. The radius ramps with TaC:Z's refit opening progress so the blur fades in/out with the screen (including
 * after it closes, while TaC:Z's closing transition runs — {@link InteractiveRefitScreen#isOpenOrClosing()}). The
 * full radius is the client config's {@code refit_screen.backgroundBlur} (0 = off).
 *
 * <p>Skipped under an Iris/Oculus shader pack: the world isn't on the main target mid-frame there (the same reason
 * the scope post-shader uses its end-of-frame path).
 */
@OnlyIn(Dist.CLIENT)
public final class RefitBlur {
    private static final ResourceLocation BLUR = ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json");

    private static PostChain chain;
    private static boolean failed = false;

    private RefitBlur() {}

    /** Run the blur if the interactive refit screen is (or is still easing) open. Call before the hand pass. */
    public static void apply(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!InteractiveRefitScreen.isOpenOrClosing()) return;
        if (IrisCompat.isShaderPackInUse()) return;
        float progress = RefitTransform.getOpeningProgress();
        if (progress <= 0f) return;
        PostChain blur = chain();
        if (blur == null) return;

        // Whole-pixel radii only: vanilla's blur.fsh divides by (2*Radius + 1) but samples whole steps, so a
        // fractional radius dims the image (by up to ~40%) — ramping through fractions made the screen pulse dark
        // while opening/closing. Below 1 there's nothing to blur.
        float radius = Math.round(RenaissanceConfig.CLIENT.refitBlur.get() * progress);
        if (radius < 1f) return;
        for (PostPass pass : ((PostChainAccessor) blur).renaissance$getPasses()) {
            pass.getEffect().safeGetUniform("Radius").set(radius);
        }
        blur.process(partialTick);

        // Back to the main target with the state the hand pass expects. The blur's last pass draws a full-screen
        // quad into the main target that also writes DEPTH, after vanilla already cleared depth for the hand pass
        // — so clear it again, or the first-person gun fails the depth test everywhere and vanishes.
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static PostChain chain() {
        if (chain != null || failed) return chain;
        Minecraft mc = Minecraft.getInstance();
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), BLUR);
            chain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        } catch (Exception e) {
            failed = true;
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Couldn't load the refit screen blur; background stays sharp", e);
        }
        return chain;
    }

    public static void resize(int width, int height) {
        if (chain != null) chain.resize(width, height);
    }

    /** Drop the chain (resource reload); it's rebuilt on next use. */
    public static void close() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
    }
}
