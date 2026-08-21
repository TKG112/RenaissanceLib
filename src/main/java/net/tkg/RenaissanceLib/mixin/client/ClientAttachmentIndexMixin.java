package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.RailRenderRegistrar;
import net.tkg.RenaissanceLib.client.underbarrel.IUnderbarrelAttachmentIndex;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelRenderRegistrar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers rail-sight renderers on a scope model once it has finished loading.
 *
 * <p>Hooks {@code loadModelsIfNecessary} right after TaC:Z configures the model (texture/scope/sight and
 * text-show renderers) and before it flags itself loaded — so by the time the model is first rendered,
 * its rail nodes already carry our {@code RailSightRenderer}s. The rail config lives in the attachment's
 * own data, which this index already holds.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientAttachmentIndex.class, remap = false)
public abstract class ClientAttachmentIndexMixin implements IUnderbarrelAttachmentIndex {

    @Shadow
    private AttachmentData data;

    @Shadow
    private BedrockAttachmentModel attachmentModel;

    @Shadow
    private AttachmentDisplay display;

    @Override
    public AttachmentDisplay renaissance$getDisplay() {
        return this.display;
    }

    @Inject(
            method = "loadModelsIfNecessary",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/resource/index/ClientAttachmentIndex;checkTextShow("
                            + "Lcom/tacz/guns/client/resource/pojo/display/attachment/AttachmentDisplay;"
                            + "Lcom/tacz/guns/client/model/BedrockAttachmentModel;)V",
                    shift = At.Shift.AFTER),
            remap = false
    )
    private void renaissance$registerRailRenderers(CallbackInfo ci) {
        RailRenderRegistrar.register(data, attachmentModel);
        UnderbarrelRenderRegistrar.register(data, display, attachmentModel);
    }
}
