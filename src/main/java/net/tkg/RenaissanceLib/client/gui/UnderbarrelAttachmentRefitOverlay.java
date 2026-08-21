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
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.network.ClientMessageSetUnderbarrelAttachment;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders and drives the installed underbarrel's <em>own</em> attachment slots in the refit screen — a muzzle,
 * extended mag, etc. mounted on the sub-gun (see {@link UnderbarrelAttachments}). Laid out exactly like the
 * canted-rail sub-slots ({@link RailRefitOverlay}): a row one step below the gun's native attachment row,
 * anchored at the grip column (where the underbarrel rides) and stacking left. Click an empty slot for a picker
 * of matching inventory attachments; click a filled one to unload. Server-authoritative via
 * {@link ClientMessageSetUnderbarrelAttachment}. Shown only in the refit overview.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class UnderbarrelAttachmentRefitOverlay {
    private static final int SIZE = GunRefitScreen.SLOT_SIZE;
    private static final int STEP = SIZE;
    private static final int MAX_PICKER = 8;
    private static final int OUTLINE = 0xFFB0A030; // muted gold — the underbarrel's own attachment slots
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

    /** The x of the {@code GRIP} native slot (where the underbarrel rides), computed like {@link GunRefitScreen}. */
    private static int gripColumnX(int screenWidth) {
        int x = screenWidth - 30;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            if (type == AttachmentType.GRIP) break;
            x -= STEP;
        }
        return x;
    }

    /** Slot {@code i} sits one row below the native attachments, stacking left from the grip column. */
    private static int slotX(int gripX, int index) {
        return gripX - index * STEP;
    }

    /**
     * One row below the native attachments — but pushed down another step when the scope's own rail row
     * ({@link RailRefitOverlay}) occupies that first row, so the two never overlap on a gun that has both.
     */
    private static int rowY(ItemStack gunItem) {
        int row = 10 + STEP;
        if (!ScopeRails.getRailSlots(gunItem).isEmpty()) row += STEP;
        return row;
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
        int gripX = gripColumnX(screen.width);
        int rowY = rowY(gunItem);

        for (int i = 0; i < types.size(); i++) {
            int x = slotX(gripX, i);
            boolean hovered = inRect(mouseX, mouseY, x, rowY, SIZE, SIZE);
            drawSlot(graphics, x, rowY, selectedSlot == i || hovered);
            ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, types.get(i));
            if (!installed.isEmpty()) {
                graphics.renderItem(installed, x + 1, rowY + 1);
            }
            graphics.renderOutline(x, rowY, SIZE, SIZE, OUTLINE);
            if (hovered) {
                int nameY = (selectedSlot == i && !installed.isEmpty()) ? rowY + 30 : rowY + 20;
                graphics.drawCenteredString(font, typeLabel(types.get(i)), x + SIZE / 2, nameY, 0xFFFFFF);
            }
        }

        if (selectedSlot >= 0 && selectedSlot < types.size()) {
            int selX = slotX(gripX, selectedSlot);
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

        renderHoverTooltip(graphics, font, player, gunItem, types, mouseX, mouseY, gripX, rowY);
    }

    private static void renderHoverTooltip(GuiGraphics graphics, Font font, LocalPlayer player, ItemStack gunItem,
                                           List<AttachmentType> types, int mouseX, int mouseY, int gripX, int rowY) {
        if (selectedSlot >= 0 && selectedSlot < types.size()
                && UnderbarrelAttachments.getInstalled(gunItem, types.get(selectedSlot)).isEmpty()) {
            int selX = slotX(gripX, selectedSlot);
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
            if (inRect(mouseX, mouseY, slotX(gripX, i), rowY, SIZE, SIZE)) {
                ItemStack installed = UnderbarrelAttachments.getInstalled(gunItem, types.get(i));
                if (!installed.isEmpty()) graphics.renderTooltip(font, installed, mouseX, mouseY);
                return;
            }
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
        int gripX = gripColumnX(screen.width);
        int rowY = rowY(gunItem);

        if (selectedSlot >= 0 && selectedSlot < types.size()) {
            int selX = slotX(gripX, selectedSlot);
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
            if (inRect(mouseX, mouseY, slotX(gripX, i), rowY, SIZE, SIZE)) {
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
