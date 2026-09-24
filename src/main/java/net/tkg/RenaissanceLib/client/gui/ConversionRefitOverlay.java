package net.tkg.RenaissanceLib.client.gui;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.tkg.RenaissanceLib.client.refit.InteractiveRefitScreen;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
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
import net.tkg.RenaissanceLib.attachment.ConversionKit;
import net.tkg.RenaissanceLib.attachment.ConversionStorage;
import net.tkg.RenaissanceLib.network.ClientMessageSetConversionKit;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders and drives the conversion-kit virtual slot in the refit screen — a single slot placed directly
 * <em>under the mag column</em> (the {@code EXTENDED_MAG} native slot), mirroring the rail system's
 * virtual-slot approach ({@link RailRefitOverlay}) rather than any native TaC:Z attachment slot.
 *
 * <p>Shown only in the refit overview ({@link RefitTransform} type {@code NONE}) and only when relevant —
 * a kit is installed, or the player is carrying one. Click the slot to focus it: empty shows a picker of
 * the inventory's conversion kits (click to install); filled shows an unload button (click to remove).
 * Both routes go through {@link ClientMessageSetConversionKit} (server-authoritative). Installing a kit
 * makes the whole weapon resolve as its converted gun, so the native slot row itself changes to the
 * converted gun's — this overlay keeps reading raw gun NBT, so the kit stays removable regardless.
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class ConversionRefitOverlay {
    private static final int SIZE = GunRefitScreen.SLOT_SIZE;
    private static final int MAX_PICKER = 8;
    private static final int CONVERSION_OUTLINE = 0xFFFFD700; // gold — the conversion slot
    private static final int UNLOAD_SIZE = 8;
    private static final int UNLOAD_DX = 5;
    private static final long INTERACT_COOLDOWN_MS = 250;

    /** Whether the conversion slot is focused (showing its unload button / kit picker). */
    private static boolean selected = false;
    private static long lastInteractTime = 0L;

    private ConversionRefitOverlay() {}

    private static boolean onCooldown() {
        return System.currentTimeMillis() - lastInteractTime < INTERACT_COOLDOWN_MS;
    }

    private static void markInteract() {
        lastInteractTime = System.currentTimeMillis();
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** The x of the {@code EXTENDED_MAG} native slot, computed exactly as {@link GunRefitScreen} lays them out. */
    private static int magColumnX(int screenWidth) {
        int x = screenWidth - 30;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            if (type == AttachmentType.EXTENDED_MAG) break;
            x -= SIZE;
        }
        return x;
    }

    private static int slotY() {
        return 10 + SIZE + 4; // directly under the native slot row (which sits at y=10)
    }

    /**
     * Whether the overlay should be active for this gun right now: the refit overview, plus a kit already
     * installed or a <em>compatible</em> kit carried — so the slot appears only on guns set up to accept one.
     */
    private static boolean shouldShow(LocalPlayer player, ItemStack gunItem) {
        if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) return false;
        return ConversionStorage.hasKit(gunItem) || !collectInventoryKits(player, gunItem).isEmpty();
    }

    // ---- Render ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        // The interactive refit screen shows this slot as a card of its own (RefitCallouts / RefitPicker).
        if (!(event.getScreen() instanceof GunRefitScreen screen) || InteractiveRefitScreen.hasCards(screen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || !shouldShow(player, gunItem)) {
            selected = false;
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        int slotX = magColumnX(screen.width);
        int slotY = slotY();

        boolean hovered = inRect(mouseX, mouseY, slotX, slotY, SIZE, SIZE);
        drawSlot(graphics, slotX, slotY, selected || hovered);
        ItemStack kit = ConversionStorage.getKit(gunItem);
        if (!kit.isEmpty()) {
            graphics.renderItem(kit, slotX + 1, slotY + 1);
        }
        graphics.renderOutline(slotX, slotY, SIZE, SIZE, CONVERSION_OUTLINE);

        if (selected) {
            if (!kit.isEmpty()) {
                int ux = slotX + UNLOAD_DX;
                int uy = slotY + SIZE + 2;
                drawUnloadButton(graphics, ux, uy, inRect(mouseX, mouseY, ux, uy, UNLOAD_SIZE, UNLOAD_SIZE));
            } else {
                int listY = slotY + SIZE + 2;
                List<Integer> kits = collectInventoryKits(player, gunItem);
                for (int j = 0; j < kits.size(); j++) {
                    int y = listY + j * SIZE;
                    drawSlot(graphics, slotX, y, inRect(mouseX, mouseY, slotX, y, SIZE, SIZE));
                    graphics.renderItem(player.getInventory().getItem(kits.get(j)), slotX + 1, y + 1);
                }
            }
        }

        renderHoverTooltip(graphics, font, player, gunItem, kit, mouseX, mouseY, slotX, slotY);
    }

    private static void renderHoverTooltip(GuiGraphics graphics, Font font, LocalPlayer player, ItemStack gunItem,
                                           ItemStack kit, int mouseX, int mouseY, int slotX, int slotY) {
        if (selected && kit.isEmpty()) {
            int listY = slotY + SIZE + 2;
            List<Integer> kits = collectInventoryKits(player, gunItem);
            for (int j = 0; j < kits.size(); j++) {
                if (inRect(mouseX, mouseY, slotX, listY + j * SIZE, SIZE, SIZE)) {
                    graphics.renderTooltip(font, player.getInventory().getItem(kits.get(j)), mouseX, mouseY);
                    return;
                }
            }
        }
        if (inRect(mouseX, mouseY, slotX, slotY, SIZE, SIZE)) {
            if (!kit.isEmpty()) {
                graphics.renderTooltip(font, kit, mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, slotLabel(), mouseX, mouseY);
            }
        }
    }

    private static Component slotLabel() {
        String key = "tooltip.renaissance_lib.conversion.slot";
        return I18n.exists(key) ? Component.translatable(key) : Component.literal("Conversion Kit");
    }

    // ---- Input -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof GunRefitScreen screen) || InteractiveRefitScreen.hasCards(screen)) return;
        if (event.getButton() != 0) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || !shouldShow(player, gunItem)) {
            selected = false;
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int slotX = magColumnX(screen.width);
        int slotY = slotY();
        ItemStack kit = ConversionStorage.getKit(gunItem);

        // Focused controls: unload (filled) or the kit picker (empty).
        if (selected) {
            if (!kit.isEmpty()) {
                if (inRect(mouseX, mouseY, slotX + UNLOAD_DX, slotY + SIZE + 2, UNLOAD_SIZE, UNLOAD_SIZE)) {
                    if (!onCooldown()) {
                        markInteract();
                        playClickSound();
                        SoundPlayManager.playerRefitSound(kit, player, SoundManager.UNINSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(
                                new ClientMessageSetConversionKit(ClientMessageSetConversionKit.CLEAR));
                    }
                    event.setCanceled(true);
                    return;
                }
            } else {
                int listY = slotY + SIZE + 2;
                List<Integer> kits = collectInventoryKits(player, gunItem);
                for (int j = 0; j < kits.size(); j++) {
                    if (inRect(mouseX, mouseY, slotX, listY + j * SIZE, SIZE, SIZE)) {
                        if (!onCooldown()) {
                            markInteract();
                            playClickSound();
                            ItemStack chosen = player.getInventory().getItem(kits.get(j));
                            SoundPlayManager.playerRefitSound(chosen, player, SoundManager.INSTALL_SOUND);
                            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetConversionKit(kits.get(j)));
                        }
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        // The conversion slot itself toggles focus.
        if (inRect(mouseX, mouseY, slotX, slotY, SIZE, SIZE)) {
            playClickSound();
            if (!onCooldown()) {
                markInteract();
                selected = !selected;
            }
            event.setCanceled(true);
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

    /** Inventory slot indices holding a conversion kit the current gun accepts (capped for the picker). */
    public static List<Integer> collectInventoryKits(LocalPlayer player, ItemStack gunItem) {
        List<Integer> result = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && result.size() < MAX_PICKER; i++) {
            if (ConversionKit.isKitCompatible(gunItem, inventory.getItem(i))) {
                result.add(i);
            }
        }
        return result;
    }
}
