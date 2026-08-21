package net.tkg.RenaissanceLib.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceConfig;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.client.AttachmentWheel;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the attachment radial wheel — the single toggle input (the old per-slot keys are gone).
 *
 * <p>Both modes are cursorless overlays ({@link AttachmentWheel}) — the mouse is diverted into the wheel's
 * pointer, so the camera doesn't turn but the player can still move, and fire/ADS/scroll are blocked
 * (see {@code MouseHandlerMixin}). They differ only in how you select, chosen by
 * {@link RenaissanceConfig.Client#holdToOpenWheel}:
 * <ul>
 *   <li><b>press</b> (default): press to open; it stays open; left-click the pointed attachment to toggle
 *       (right-click or press the key again to cancel).</li>
 *   <li><b>hold</b>: hold to open; release on the pointed attachment to toggle (release centred, or
 *       right-click, to cancel).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class AttachmentWheelKey {

    public static final KeyMapping OPEN_WHEEL = new KeyMapping(
            "key.renaissance_lib.attachment_wheel.desc",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.category.renaissance_lib");

    private static boolean wasDown = false;

    private AttachmentWheelKey() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean hold = RenaissanceConfig.CLIENT.holdToOpenWheel.get();

        // Close if the wheel can no longer be shown or the player switched items. (Both modes are overlays
        // now — no screen — so movement works while the wheel is open.)
        if (AttachmentWheel.isOpen()) {
            if (mc.player == null || mc.screen != null
                    || mc.player.getInventory().selected != AttachmentWheel.openSlot()) {
                AttachmentWheel.close();
            }
        }

        boolean down = !OPEN_WHEEL.isUnbound() && OPEN_WHEEL.isDown();

        if (hold) {
            if (down && !wasDown) {
                AttachmentWheel.tryOpen();
            } else if (!down && wasDown && AttachmentWheel.isOpen()) {
                AttachmentWheel.confirm();
            }
        } else if (down && !wasDown) {
            // Press mode: first press opens; pressing again cancels (close without toggling). Selection is a
            // left-click (handled in MouseHandlerMixin); right-click also cancels.
            if (AttachmentWheel.isOpen()) {
                AttachmentWheel.close();
            } else {
                AttachmentWheel.tryOpen();
            }
        }
        wasDown = down;
    }
}
