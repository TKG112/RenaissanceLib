package net.tkg.RenaissanceLib.client.gui;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.network.ClientMessageSetUnderbarrelAttachment;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders and drives the installed underbarrel's <em>own</em> attachment slots in the refit screen — a muzzle,
 * extended mag, etc. mounted on the sub-gun (see {@link UnderbarrelAttachments}). Laid out identically to the
 * canted-rail sub-slots ({@link RailRefitOverlay}) and sharing its layout helpers: a host marker (the
 * underbarrel) in the right-anchored column with slots stacking left, one row below the gun's native
 * attachments and any rail host rows (tucked close, and pushed down while a rail slot's panel is showing).
 * Click an empty slot for a picker of matching inventory attachments; click a filled one to unload.
 * Server-authoritative via {@link ClientMessageSetUnderbarrelAttachment}. Shown only in the refit overview.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class UnderbarrelAttachmentRefitOverlay {
    private static final int SIZE = GunRefitScreen.SLOT_SIZE;
    private static final int MAX_PICKER = 8;
    private static final int MARKER_OUTLINE = 0xFFFFA500; // orange — the host marker, matching the rail rows
    private static final int UNLOAD_SIZE = 8;
    private static final int UNLOAD_DX = 5;
    private static final long INTERACT_COOLDOWN_MS = 250;

    private static int selectedSlot = -1;
    private static long lastInteractTime = 0L;

    private UnderbarrelAttachmentRefitOverlay() {}

    private static boolean onCooldown() {
        return System.currentTimeMillis() - lastInteractTime < INTERACT_COOLDOWN_MS;
    }

    private static void markInteract() {
        lastInteractTime = System.currentTimeMillis();
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The shared right-anchored host-marker column — identical to the canted-rail rows ({@link RailRefitOverlay}). */
    private static int anchorX(int screenWidth) {
        return RailRefitOverlay.anchorX(screenWidth);
    }

    /** Slot {@code i} stacks left of the host marker, exactly like a rail row. */
    private static int slotX(int anchorX, int index) {
        return RailRefitOverlay.slotColumnX(anchorX, index);
    }

    /**
     * The underbarrel's own attachment row sits just below the gun's native attachments and any rail host rows
     * — close by default, and pushed down while a rail slot is being handled (its panel is showing).
     */
    private static int rowY(LocalPlayer player, ItemStack gunItem) {
        return RailRefitOverlay.subRowBottomY(player, gunItem);
    }

    /** The underbarrel's allowed attachment types, or empty if no underbarrel installed. */
    private static List<AttachmentType> slots(ItemStack gunItem) {
        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gunItem));
        return ubData == null ? List.of() : UnderbarrelAttachments.getAllowedTypes(ubData);
    }

    private static boolean active(ItemStack gunItem) {
        return RefitTransform.getCurrentTransformType() == AttachmentType.NONE && !slots(gunItem).isEmpty();
    }

    // ---- Render ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GunRefitScreen screen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null || !active(gunItem)) {
            selectedSlot = -1;
            return;
        }

        List<AttachmentType> types = slots(gunItem);
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        int anchorX = anchorX(screen.width);
        int rowY = rowY(player, gunItem);

        // Host marker: the underbarrel itself, at the anchor column — the same place a rail row shows its host.
        drawSlot(graphics, anchorX, rowY, false);
        ItemStack ub = Underbarrel.getInstalledUnderbarrel(gunItem);
        if (!ub.isEmpty()) graphics.renderItem(ub, anchorX + 1, rowY + 1);
        graphics.renderOutline(anchorX, rowY, SIZE, SIZE, MARKER_OUTLINE);

        for (int i = 0; i < types.size(); i++) {
            int x = slotX(anchorX, i);
            boolean hovered = inRect(mouseX, mouseY, x, rowY, SIZE, SIZE);
            drawSlot(graphics, x, rowY, selectedSlot == i || hovered);
            ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, types.get(i));
            if (!installed.isEmpty()) {
                graphics.renderItem(installed, x + 1, rowY + 1);
            }
            if (hovered) {
                int nameY = (selectedSlot == i && !installed.isEmpty()) ? rowY + 30 : rowY + 20;
                graphics.drawCenteredString(font, typeLabel(types.get(i)), x + SIZE / 2, nameY, 0xFFFFFF);
            }
        }

        if (selectedSlot >= 0 && selectedSlot < types.size()) {
            int selX = slotX(anchorX, selectedSlot);
            ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, types.get(selectedSlot));
            if (!installed.isEmpty()) {
                int ux = selX + UNLOAD_DX;
                int uy = rowY + SIZE + 2;
                drawUnloadButton(graphics, ux, uy, inRect(mouseX, mouseY, ux, uy, UNLOAD_SIZE, UNLOAD_SIZE));
            } else {
                int listY = rowY + 2 * SIZE;
                List<Integer> picks = collectInventory(player, types.get(selectedSlot));
                for (int j = 0; j < picks.size(); j++) {
                    int y = listY + j * SIZE;
                    drawSlot(graphics, selX, y, inRect(mouseX, mouseY, selX, y, SIZE, SIZE));
                    graphics.renderItem(player.getInventory().getItem(picks.get(j)), selX + 1, y + 1);
                }
            }
        }

        renderHoverTooltip(graphics, font, player, gunItem, types, mouseX, mouseY, anchorX, rowY);
    }

    private static void renderHoverTooltip(GuiGraphics graphics, Font font, LocalPlayer player, ItemStack gunItem,
                                           List<AttachmentType> types, int mouseX, int mouseY, int anchorX, int rowY) {
        if (selectedSlot >= 0 && selectedSlot < types.size()
                && UnderbarrelAttachments.getInstalled(gunItem, types.get(selectedSlot)).isEmpty()) {
            int selX = slotX(anchorX, selectedSlot);
            int listY = rowY + 2 * SIZE;
            List<Integer> picks = collectInventory(player, types.get(selectedSlot));
            for (int j = 0; j < picks.size(); j++) {
                if (inRect(mouseX, mouseY, selX, listY + j * SIZE, SIZE, SIZE)) {
                    graphics.renderTooltip(font, player.getInventory().getItem(picks.get(j)), mouseX, mouseY);
                    return;
                }
            }
        }
        for (int i = 0; i < types.size(); i++) {
            if (inRect(mouseX, mouseY, slotX(anchorX, i), rowY, SIZE, SIZE)) {
                ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, types.get(i));
                if (!installed.isEmpty()) graphics.renderTooltip(font, installed, mouseX, mouseY);
                return;
            }
        }
        // Host marker → the underbarrel itself.
        if (inRect(mouseX, mouseY, anchorX, rowY, SIZE, SIZE)) {
            ItemStack ub = Underbarrel.getInstalledUnderbarrel(gunItem);
            if (!ub.isEmpty()) graphics.renderTooltip(font, ub, mouseX, mouseY);
        }
    }

    private static Component typeLabel(AttachmentType type) {
        String n = type.name().toLowerCase();
        return Component.literal(Character.toUpperCase(n.charAt(0)) + n.substring(1));
    }

    // ---- Input -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof GunRefitScreen screen)) return;
        if (event.getButton() != 0) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null || !active(gunItem)) {
            selectedSlot = -1;
            return;
        }

        List<AttachmentType> types = slots(gunItem);
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int anchorX = anchorX(screen.width);
        int rowY = rowY(player, gunItem);

        if (selectedSlot >= 0 && selectedSlot < types.size()) {
            int selX = slotX(anchorX, selectedSlot);
            AttachmentType type = types.get(selectedSlot);
            ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, type);
            if (!installed.isEmpty()
                    && inRect(mouseX, mouseY, selX + UNLOAD_DX, rowY + SIZE + 2, UNLOAD_SIZE, UNLOAD_SIZE)) {
                if (!onCooldown()) {
                    markInteract();
                    playClickSound();
                    SoundPlayManager.playerRefitSound(installed, player, SoundManager.UNINSTALL_SOUND);
                    NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetUnderbarrelAttachment(
                            type, ClientMessageSetUnderbarrelAttachment.CLEAR));
                }
                event.setCanceled(true);
                return;
            }
            int listY = rowY + 2 * SIZE;
            List<Integer> picks = collectInventory(player, type);
            for (int j = 0; j < picks.size(); j++) {
                if (inRect(mouseX, mouseY, selX, listY + j * SIZE, SIZE, SIZE)) {
                    if (!onCooldown()) {
                        markInteract();
                        playClickSound();
                        ItemStack chosen = player.getInventory().getItem(picks.get(j));
                        SoundPlayManager.playerRefitSound(chosen, player, SoundManager.INSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(
                                new ClientMessageSetUnderbarrelAttachment(type, picks.get(j)));
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }

        for (int i = 0; i < types.size(); i++) {
            if (inRect(mouseX, mouseY, slotX(anchorX, i), rowY, SIZE, SIZE)) {
                playClickSound();
                if (!onCooldown()) {
                    markInteract();
                    selectedSlot = (selectedSlot == i) ? -1 : i;
                }
                event.setCanceled(true);
                return;
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static void drawSlot(GuiGraphics graphics, int x, int y, boolean outlined) {
        if (outlined) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, SIZE, SIZE, SIZE, SIZE);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, SIZE - 2, SIZE - 2, SIZE, SIZE);
        }
    }

    private static void drawUnloadButton(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.blit(GunRefitScreen.UNLOAD_TEXTURE, x, y, UNLOAD_SIZE, UNLOAD_SIZE,
                hovered ? 80f : 0f, 0f, 80, 80, 160, 80);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Inventory slot indices holding an attachment of {@code type} (capped for the picker). */
    private static List<Integer> collectInventory(LocalPlayer player, AttachmentType type) {
        List<Integer> result = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && result.size() < MAX_PICKER; i++) {
            ItemStack stack = inventory.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment != null && attachment.getType(stack) == type) {
                result.add(i);
            }
        }
        return result;
    }
}
