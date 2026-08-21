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
 * Renders the fire-mode radial wheel — the cursorless overlay driven by {@link FireModeWheel}, with look
 * input diverted into a pointer by {@code MouseHandlerMixin} (via {@link WheelInput}).
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class FireModeWheelOverlay {

    private FireModeWheelOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!FireModeWheel.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        if (IGun.getIGunOrNull(gun) == null) return;

        GuiGraphics gg = event.getGuiGraphics();
        double pointerAngle = FireModeWheel.isOpen() ? FireModeWheel.pointerAngleDeg() : Double.NaN;
        FireModeWheelRenderer.draw(gg, FireModeWheel.choices(), FireModeWheel.highlighted(),
                gg.guiWidth() / 2, gg.guiHeight() / 2, FireModeWheel.renderAlpha(), pointerAngle);
    }
}
