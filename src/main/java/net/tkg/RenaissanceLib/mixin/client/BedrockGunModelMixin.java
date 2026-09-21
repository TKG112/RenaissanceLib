package net.tkg.RenaissanceLib.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.util.RenderHelper;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.llamalad7.mixinextras.sugar.Local;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import net.tkg.RenaissanceLib.client.ActiveOptic;
import net.tkg.RenaissanceLib.client.RailAim;
import net.tkg.RenaissanceLib.client.RailGunModelContext;
import net.tkg.RenaissanceLib.compat.TaczDescriptors;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;

/**
 * Clips the gun body inside a mounted rail optic's ocular while the player is scoped through it — the
 * way TaC:Z clips the gun out of a main scope's ocular.
 *
 * <p>TaC:Z only enables the gun-body stencil test for the scope-slot attachment; a canted rail is a
 * sight-type mount, so it never fires and the barrel shows through a rail optic. When a masking rail
 * optic (a scope — only a scope clips in the rail system; see {@link RailAim#masksOptic}) is the active
 * optic, that optic has already written its ocular into the stencil during the scope-slot render, so we
 * just re-enable the matching stencil test right before the gun body draws.
 *
 * <p>(Clipping the rail mount and any <em>other</em> mounted optics out of the active optic's lens too
 * isn't done: TaC:Z draws a scope's backdrop at a fixed near depth and each scope render clears the
 * shared stencil, so a second optic can't be composited into the lens without a stencil-only pre-pass
 * rewrite of TaC:Z's scope pipeline.)
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class BedrockGunModelMixin {

    /** Aiming progress past which the gun-body clip runs (essentially "aiming at all"). */
    private static final float RENAISSANCE_MASK_AIM = 0.05f;

    @Shadow
    @Final
    private EnumMap<AttachmentType, ItemStack> currentAttachmentItem;

    /**
     * Hides the host gun's tactical handguard when the installed underbarrel declares
     * {@code hide_tactical_handguard}. TaC:Z forces the tactical handguard visible whenever a GRIP (the
     * slot the underbarrel rides) is installed, which would clash with the underbarrel's own handguard
     * geometry. We run right after TaC:Z sets the node's visibility and override it back off for such an
     * underbarrel — the underbarrel's dedicated handguard adapter node still renders natively.
     */
    @Inject(
            method = "handguardTacticalRender(Lcom/tacz/guns/client/model/bedrock/BedrockPart;)"
                    + "Lcom/tacz/guns/client/model/IFunctionalRenderer;",
            at = @At("RETURN"),
            remap = false
    )
    private void renaissance$hideTacticalHandguardForUnderbarrel(
            BedrockPart part, CallbackInfoReturnable<IFunctionalRenderer> cir) {
        ItemStack grip = currentAttachmentItem.get(AttachmentType.GRIP);
        if (grip == null || grip.isEmpty()) return;
        if (UnderbarrelClient.isHideTacticalHandguard(grip)) {
            part.visible = false;
        }
    }

    // The first-person gun render is overloaded in the TaC:Z beta (the stencil masking moved to a BufferSource
    // overload), so the exact descriptor is variant-specific — see TaczDescriptors in src/tacz_<variant>/java.
    private static final String RENDER_DESC = TaczDescriptors.RENDER;

    // NB: these render injects capture ZERO target args (just CallbackInfo), and read the couple of values they
    // need via @Local(argsOnly=true). This keeps them valid across TaC:Z versions — the beta appended args
    // (floats + MultiBufferSource) to render, and Mixin requires a captured-arg list to match the target
    // exactly, so capturing the old arg list would fail. @Local captures by type/ordinal among the leading
    // args, which are unchanged.

    /** Publish this gun model while it renders, so a rail laser's draw can be hoisted onto its delegate. */
    @Inject(method = RENDER_DESC, at = @At("HEAD"), remap = false)
    private void renaissance$beginGunModelContext(CallbackInfo ci) {
        RailGunModelContext.begin((BedrockGunModel) (Object) this);
    }

    @Inject(method = RENDER_DESC, at = @At("RETURN"), remap = false)
    private void renaissance$endGunModelContext(CallbackInfo ci) {
        RailGunModelContext.end();
    }

    @Inject(
            method = RENDER_DESC,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;stencilOp(III)V",
                    shift = At.Shift.AFTER),
            remap = false
    )
    private void renaissance$maskGunInRailScope(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) ItemStack gunItem,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType) {
        if (!TaczCompat.CLIP_IN_LENS) return; // buffered pipeline (beta): immediate stencil mask breaks the lens
        if (!transformType.firstPerson()) return;
        if (renaissance$aimingProgress() <= RENAISSANCE_MASK_AIM) return;
        ActiveOptic optic = ActiveOptic.resolve(gunItem);
        if (optic == null || optic.isScope()) return; // scope-slot masking is TaC:Z's own job
        ClientAttachmentIndex index = optic.index();
        if (!RailAim.masksOptic(index)) return;
        RenderHelper.enableItemEntityStencilTest();
        RenderSystem.stencilFunc(RailAim.maskFunc(index), RailAim.maskRef(index), 0xFF);
    }

    /**
     * Accelerated-Rendering counterpart of {@link #renaissance$maskGunInRailScope}. Under the
     * {@code acceleratedrendering} mod, TaC:Z draws the gun body deferred on AR layer -940 with a before-runnable
     * that sets up the gun-body stencil only for a masking <em>scope-slot</em> optic (from bytecode: the before
     * function keys on the scope-slot index's isScope/isSight). For a masking <em>rail</em> optic that logic
     * doesn't fire, so the barrel shows through the rail lens. We override the before-runnable with the rail
     * optic's mask stencil, injected right before the gun body's deferred render (after TaC:Z has already set
     * the -940 layer + its own after-runnable, which stays intact and resets the stencil).
     *
     * <p>BLIND: the {@code acceleratedrendering} mod isn't in the dev workspace, so this path never runs here;
     * needs in-game verification with the mod installed.
     */
    @Inject(
            method = "renderAccelerated",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/model/BedrockAnimatedModel;render("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;"
                            + "Lnet/minecraft/client/renderer/RenderType;II)V",
                    shift = At.Shift.BEFORE),
            remap = false, require = 0
    )
    private void renaissance$maskGunInRailScopeAccelerated(
            CallbackInfo ci,
            @Local(argsOnly = true, ordinal = 0) ItemStack gunItem,
            @Local(argsOnly = true, ordinal = 0) ItemDisplayContext transformType) {
        if (!transformType.firstPerson()) return;
        if (renaissance$aimingProgress() <= RENAISSANCE_MASK_AIM) return;
        ActiveOptic optic = ActiveOptic.resolve(gunItem);
        if (optic == null || optic.isScope()) return; // scope-slot masking is TaC:Z's own job
        ClientAttachmentIndex index = optic.index();
        if (!RailAim.masksOptic(index)) return;
        int func = RailAim.maskFunc(index);
        int ref = RailAim.maskRef(index);
        ARCompat.setRenderBeforeFunction(() -> {
            RenderHelper.enableItemEntityStencilTest();
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilFunc(func, ref, 0xFF);
        });
    }

    private static float renaissance$aimingProgress() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0f;
        try {
            return IClientPlayerGunOperator.fromLocalPlayer(player)
                    .getClientAimingProgress(Minecraft.getInstance().getFrameTime());
        } catch (Throwable t) {
            return 0f;
        }
    }
}
