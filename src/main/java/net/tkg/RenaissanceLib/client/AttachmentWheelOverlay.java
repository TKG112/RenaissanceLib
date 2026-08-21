package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

/**
 * Renders the attachment wheel (both press and hold modes) — the cursorless overlay driven by
 * {@link AttachmentWheel}, with look input diverted into a pointer by {@code MouseHandlerMixin}.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class AttachmentWheelOverlay {

    private AttachmentWheelOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!AttachmentWheel.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;

        GuiGraphics gg = event.getGuiGraphics();
        // Freeze the arrow during the fade-out (once closed, don't keep tracking the pointer).
        double pointerAngle = AttachmentWheel.isOpen() ? AttachmentWheel.pointerAngleDeg() : Double.NaN;
        AttachmentWheelRenderer.draw(gg, gun, AttachmentWheel.targets(), AttachmentWheel.highlighted(),
                gg.guiWidth() / 2, gg.guiHeight() / 2, AttachmentWheel.renderAlpha(), pointerAngle);
    }
}
