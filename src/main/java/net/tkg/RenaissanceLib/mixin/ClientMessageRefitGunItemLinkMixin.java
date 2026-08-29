package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.tkg.RenaissanceLib.attachment.IClientMessageRefitGun;
import net.tkg.RenaissanceLib.attachment.ItemLinkInstall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts TaC:Z's attachment install so that installing a "grip" whose inventory item is actually the linked
 * gun (see {@link ItemLinkInstall}) does our round-trip install instead. TaC:Z's own handler already no-ops for
 * a gun item (it isn't a valid attachment), so we detect the case first and cancel it when we handle it.
 */
@Mixin(value = ClientMessageRefitGun.class, remap = false)
public abstract class ClientMessageRefitGunItemLinkMixin implements IClientMessageRefitGun {

    @Accessor("attachmentSlotIndex")
    @Override
    public abstract int renaissance$attachmentSlotIndex();

    @Accessor("attachmentType")
    @Override
    public abstract AttachmentType renaissance$attachmentType();

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void renaissance$linkedInstall(NetworkEvent.Context ctx, ClientMessageRefitGun msg,
                                                  CallbackInfo ci) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        IClientMessageRefitGun m = (IClientMessageRefitGun) (Object) msg;
        if (ItemLinkInstall.tryInstall(player, m.renaissance$attachmentSlotIndex(), m.renaissance$attachmentType())) {
            ci.cancel(); // we installed the underbarrel from the linked gun; skip TaC:Z's normal path
        }
    }
}
