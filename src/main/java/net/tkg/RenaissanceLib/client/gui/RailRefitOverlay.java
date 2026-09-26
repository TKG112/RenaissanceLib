package net.tkg.RenaissanceLib.client.gui;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.tkg.RenaissanceLib.client.refit.InteractiveRefitScreen;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.LaserColorUtil;
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
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.network.ClientMessageSetRailLaserColor;
import net.tkg.RenaissanceLib.network.ClientMessageSetRailSight;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.awt.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders and drives the rail mount slots in the refit screen — now a <em>tree</em> of mounts. The row
 * shows the slots of the current host ({@link #viewPath}, {@code ROOT} = the installed scope). A mounted
 * optic that itself declares rails can be <em>entered</em> to show its sub-slots (with a back button to
 * pop up a level), so an author's nested optic stacks can be built entirely in-screen.
 *
 * <p>Per slot: click empty → a picker of matching inventory optics (filtered by the slot's {@code allow}
 * and the SCOPE-host guardrail); click filled → unload, plus an "enter" affordance when it has sub-rails.
 * Installs/removes target a {@link MountPath} through {@link ClientMessageSetRailSight}
 * (server-authoritative).
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class RailRefitOverlay {
    private static final int SIZE = GunRefitScreen.SLOT_SIZE;
    private static final int STEP = SIZE;
    /** Vertical pitch between stacked host rows — a slot plus a gap for the hover name / unload button. */
    private static final int ROW_PITCH = SIZE + 12;
    private static final int ROW_Y0 = 10 + STEP;
    /** Small gap between sub-slot groups (the rail rows, their panel, and the underbarrel's row). */
    private static final int GROUP_GAP = 3;
    private static final int MAX_PICKER = 8;
    private static final int DISPLAY_OUTLINE = 0xFFFFA500; // orange — the current host
    private static final int BACK_OUTLINE = 0xFF66CCFF;    // blue — the back button
    private static final int ENTER_OUTLINE = 0xFF66FF99;   // green — a mount you can enter
    private static final int UNLOAD_SIZE = 8;
    private static final int UNLOAD_DX = 5;

    private static final long INTERACT_COOLDOWN_MS = 250;

    /** The focused host + drill path; its {@link MountPath#hostType()} is the focused native slot. */
    private static MountPath viewPath = MountPath.ROOT;
    /** The focused slot within the focused host, or -1. */
    private static int selectedSlot = -1;
    private static long lastInteractTime = 0L;

    // ---- Laser color sliders (shown when the focused mount is a colour-editable laser) --------------
    private static final int SLIDER_W = 140;
    private static final int SLIDER_H = 10;
    /** Which HSV slider is being dragged: 0 = hue, 1 = saturation, -1 = none. */
    private static int draggingSlider = -1;

    private RailRefitOverlay() {}

    private static boolean onCooldown() {
        return System.currentTimeMillis() - lastInteractTime < INTERACT_COOLDOWN_MS;
    }

    private static void markInteract() {
        lastInteractTime = System.currentTimeMillis();
    }

    private static void resetNav() {
        viewPath = MountPath.ROOT;
        selectedSlot = -1;
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- Tree helpers ----------------------------------------------------------------------------

    /** The rail slots of the focused host at its current drill level ({@link #viewPath}). */
    private static List<RailsModifier.RailSlot> currentSlots(ItemStack gunItem) {
        if (viewPath.isRoot()) {
            RailsModifier.Spec spec = ScopeRails.getRailsSpecForType(gunItem, viewPath.hostType());
            return spec == null ? List.of() : spec.getSlots();
        }
        RailsModifier.Spec spec = ScopeRails.getRailsSpecForAttachment(RailStorage.getMountedOnGun(gunItem, viewPath));
        return spec == null ? List.of() : spec.getSlots();
    }

    /** The item mounted in {@code slot} of the focused host. */
    private static ItemStack mountedAt(ItemStack gunItem, int slot) {
        return RailStorage.getMountedOnGun(gunItem, viewPath.child(slot));
    }

    /** The focused host attachment itself (the host slot's attachment at the root, else the mount at {@link #viewPath}). */
    private static ItemStack currentHost(ItemStack gunItem, IGun iGun) {
        return viewPath.isRoot() ? iGun.getAttachment(gunItem, viewPath.hostType())
                : RailStorage.getMountedOnGun(gunItem, viewPath);
    }

    private static boolean hasSubRails(ItemStack mounted) {
        return ScopeRails.getRailsSpecForAttachment(mounted) != null;
    }

    /** Keeps {@link #viewPath} valid: pop to the focused host's root if the drilled mount is gone. */
    private static void ensureValidView(ItemStack gunItem) {
        if (!viewPath.isRoot() && RailStorage.getMountedOnGun(gunItem, viewPath).isEmpty()) {
            viewPath = MountPath.root(viewPath.hostType());
            selectedSlot = -1;
        }
    }

    /** Focuses the first rail host if the current {@link #viewPath} host no longer carries rails. */
    private static void focusValidHost(List<ScopeRails.RailHost> hosts) {
        for (ScopeRails.RailHost host : hosts) {
            if (host.type() == viewPath.hostType()) return;
        }
        viewPath = MountPath.root(hosts.get(0).type());
        selectedSlot = -1;
    }

    private static int hostRowY(int hostIndex) {
        return ROW_Y0 + hostIndex * ROW_PITCH;
    }

    /** Bottom edge of the last rail host row's slots. */
    private static int railBlockBottom(ItemStack gunItem) {
        int hosts = ScopeRails.getRailHosts(gunItem).size();
        return ROW_Y0 + Math.max(0, hosts - 1) * ROW_PITCH + SIZE;
    }

    /** Y of the focused-slot panel (picker / unload), tucked just below the host rows. */
    private static int panelY(ItemStack gunItem) {
        return railBlockBottom(gunItem) + GROUP_GAP;
    }

    private static int indexOfHost(List<ScopeRails.RailHost> hosts, AttachmentType type) {
        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).type() == type) return i;
        }
        return 0;
    }

    /** The mount in {@code slot} of a specific host root (used for non-focused host rows). */
    private static ItemStack mountedAtHost(ItemStack gunItem, AttachmentType hostType, int slot) {
        return RailStorage.getMountedOnGun(gunItem, MountPath.root(hostType).child(slot));
    }

    // ---- Render ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof GunRefitScreen screen) || InteractiveRefitScreen.hasCards(event.getScreen())) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        List<ScopeRails.RailHost> hosts = ScopeRails.getRailHosts(gunItem);
        if (hosts.isEmpty() || RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            resetNav();
            return;
        }
        focusValidHost(hosts);
        ensureValidView(gunItem);

        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        int displayX = screen.width - 30;
        Font font = Minecraft.getInstance().font;

        // One row per rail host, stacked downward. The focused host shows its current drill level.
        for (int h = 0; h < hosts.size(); h++) {
            ScopeRails.RailHost host = hosts.get(h);
            boolean focused = host.type() == viewPath.hostType();
            int rowY = hostRowY(h);

            drawSlot(graphics, displayX, rowY, false);
            ItemStack hostItem = focused ? currentHost(gunItem, iGun) : host.item();
            if (!hostItem.isEmpty()) graphics.renderItem(hostItem, displayX + 1, rowY + 1);
            int markerOutline = focused ? (viewPath.isRoot() ? DISPLAY_OUTLINE : BACK_OUTLINE) : 0x66FFFFFF;
            graphics.renderOutline(displayX, rowY, SIZE, SIZE, markerOutline);

            List<RailsModifier.RailSlot> slots = focused ? currentSlots(gunItem) : host.spec().getSlots();
            for (int i = 0; i < slots.size(); i++) {
                int x = railSlotX(displayX, i);
                boolean hovered = inSlot(mouseX, mouseY, x, rowY);
                drawSlot(graphics, x, rowY, (focused && selectedSlot == i) || hovered);
                ItemStack mounted = focused ? mountedAt(gunItem, i) : mountedAtHost(gunItem, host.type(), i);
                if (!mounted.isEmpty()) {
                    graphics.renderItem(mounted, x + 1, rowY + 1);
                    if (hasSubRails(mounted)) graphics.renderOutline(x, rowY, SIZE, SIZE, ENTER_OUTLINE);
                }
                if (hovered) {
                    graphics.drawCenteredString(font, railSlotName(slots, i), x + SIZE / 2, rowY + SIZE + 2, 0xFFFFFF);
                }
            }
        }

        // Focused-slot panel (unload for a filled slot, else the inventory picker), below every host row.
        List<RailsModifier.RailSlot> focusedSlots = currentSlots(gunItem);
        if (selectedSlot >= 0 && selectedSlot < focusedSlots.size()) {
            int selX = railSlotX(displayX, selectedSlot);
            int panelY = panelY(gunItem);
            ItemStack mounted = mountedAt(gunItem, selectedSlot);
            if (!mounted.isEmpty()) {
                drawUnloadButton(graphics, selX + UNLOAD_DX, panelY,
                        inRect(mouseX, mouseY, selX + UNLOAD_DX, panelY, UNLOAD_SIZE, UNLOAD_SIZE));
            } else {
                List<Integer> optics = collectInventorySights(player,
                        focusedSlots.get(selectedSlot), viewPath.hostType());
                for (int j = 0; j < optics.size(); j++) {
                    int y = panelY + j * SIZE;
                    drawSlot(graphics, selX, y, inSlot(mouseX, mouseY, selX, y));
                    graphics.renderItem(player.getInventory().getItem(optics.get(j)), selX + 1, y + 1);
                }
            }
        }

        handleAndRenderLaserSliders(graphics, screen, gunItem, mouseX);
        renderHoverTooltip(graphics, player, gunItem, iGun, hosts, mouseX, mouseY, displayX);
    }

    private static void renderHoverTooltip(GuiGraphics graphics, LocalPlayer player, ItemStack gunItem, IGun iGun,
                                           List<ScopeRails.RailHost> hosts, int mouseX, int mouseY, int displayX) {
        Font font = Minecraft.getInstance().font;

        // Picker entries of the focused empty slot.
        List<RailsModifier.RailSlot> focusedSlots = currentSlots(gunItem);
        if (selectedSlot >= 0 && selectedSlot < focusedSlots.size()
                && mountedAt(gunItem, selectedSlot).isEmpty()) {
            int selX = railSlotX(displayX, selectedSlot);
            int panelY = panelY(gunItem);
            List<Integer> optics = collectInventorySights(player,
                    focusedSlots.get(selectedSlot), viewPath.hostType());
            for (int j = 0; j < optics.size(); j++) {
                if (inSlot(mouseX, mouseY, selX, panelY + j * SIZE)) {
                    graphics.renderTooltip(font, player.getInventory().getItem(optics.get(j)), mouseX, mouseY);
                    return;
                }
            }
        }

        // Mounted items and host markers across all host rows.
        for (int h = 0; h < hosts.size(); h++) {
            ScopeRails.RailHost host = hosts.get(h);
            boolean focused = host.type() == viewPath.hostType();
            int rowY = hostRowY(h);
            List<RailsModifier.RailSlot> slots = focused ? currentSlots(gunItem) : host.spec().getSlots();
            for (int i = 0; i < slots.size(); i++) {
                if (inSlot(mouseX, mouseY, railSlotX(displayX, i), rowY)) {
                    ItemStack mounted = focused ? mountedAt(gunItem, i) : mountedAtHost(gunItem, host.type(), i);
                    if (!mounted.isEmpty()) graphics.renderTooltip(font, mounted, mouseX, mouseY);
                    return;
                }
            }
            if (inSlot(mouseX, mouseY, displayX, rowY)) {
                ItemStack hostItem = focused ? currentHost(gunItem, iGun) : host.item();
                if (!hostItem.isEmpty()) graphics.renderTooltip(font, hostItem, mouseX, mouseY);
                return;
            }
        }
    }

    public static Component railSlotName(List<RailsModifier.RailSlot> slots, int index) {
        String type = (index >= 0 && index < slots.size()) ? slots.get(index).getType() : "generic";
        String key = "tooltip.renaissance_lib.rail." + type;
        if (I18n.exists(key)) {
            return Component.translatable(key);
        }
        return Component.literal(type.isEmpty() ? "Rail" : Character.toUpperCase(type.charAt(0)) + type.substring(1));
    }

    // ---- Input -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof GunRefitScreen screen) || InteractiveRefitScreen.hasCards(event.getScreen())) return;
        if (event.getButton() != 0) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        List<ScopeRails.RailHost> hosts = ScopeRails.getRailHosts(gunItem);
        if (hosts.isEmpty() || RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            resetNav();
            return;
        }
        focusValidHost(hosts);
        ensureValidView(gunItem);

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int displayX = screen.width - 30;

        // Laser colour sliders (bottom-left) take priority when a colour-editable laser is focused.
        if (laserSliderPress(screen, gunItem, mouseX, mouseY)) {
            event.setCanceled(true);
            return;
        }

        // Focused-slot panel controls (unload / picker), below all host rows.
        List<RailsModifier.RailSlot> focusedSlots = currentSlots(gunItem);
        if (selectedSlot >= 0 && selectedSlot < focusedSlots.size()) {
            int selX = railSlotX(displayX, selectedSlot);
            int panelY = panelY(gunItem);
            ItemStack mounted = mountedAt(gunItem, selectedSlot);
            if (!mounted.isEmpty()) {
                if (inRect(mouseX, mouseY, selX + UNLOAD_DX, panelY, UNLOAD_SIZE, UNLOAD_SIZE)) {
                    if (!onCooldown()) {
                        markInteract();
                        playClickSound();
                        SoundPlayManager.playerRefitSound(mounted, player, SoundManager.UNINSTALL_SOUND);
                        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetRailSight(
                                viewPath.child(selectedSlot), ClientMessageSetRailSight.CLEAR));
                    }
                    event.setCanceled(true);
                    return;
                }
            } else {
                List<Integer> optics = collectInventorySights(player,
                        focusedSlots.get(selectedSlot), viewPath.hostType());
                for (int j = 0; j < optics.size(); j++) {
                    if (inSlot(mouseX, mouseY, selX, panelY + j * SIZE)) {
                        if (!onCooldown()) {
                            markInteract();
                            playClickSound();
                            ItemStack chosen = player.getInventory().getItem(optics.get(j));
                            SoundPlayManager.playerRefitSound(chosen, player, SoundManager.INSTALL_SOUND);
                            NetworkHandler.CHANNEL.sendToServer(
                                    new ClientMessageSetRailSight(viewPath.child(selectedSlot), optics.get(j)));
                        }
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }

        // Host rows: markers (focus / back) and slots (focus + select / drill-in).
        for (int h = 0; h < hosts.size(); h++) {
            ScopeRails.RailHost host = hosts.get(h);
            boolean focused = host.type() == viewPath.hostType();
            int rowY = hostRowY(h);

            if (inSlot(mouseX, mouseY, displayX, rowY)) {
                playClickSound();
                if (!onCooldown()) {
                    markInteract();
                    if (focused && !viewPath.isRoot()) {
                        viewPath = viewPath.parent(); // back up one drill level
                    } else if (!focused) {
                        viewPath = MountPath.root(host.type()); // focus this host
                    }
                    selectedSlot = -1;
                }
                event.setCanceled(true);
                return;
            }

            List<RailsModifier.RailSlot> slots = focused ? currentSlots(gunItem) : host.spec().getSlots();
            for (int i = 0; i < slots.size(); i++) {
                if (inSlot(mouseX, mouseY, railSlotX(displayX, i), rowY)) {
                    playClickSound();
                    if (!onCooldown()) {
                        markInteract();
                        if (!focused) {
                            viewPath = MountPath.root(host.type());
                            selectedSlot = i;
                        } else {
                            ItemStack mounted = mountedAt(gunItem, i);
                            if (!mounted.isEmpty() && hasSubRails(mounted) && selectedSlot == i) {
                                viewPath = viewPath.child(i); // drill into a mount with sub-rails
                                selectedSlot = -1;
                            } else {
                                selectedSlot = (selectedSlot == i) ? -1 : i;
                            }
                        }
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** Ends a laser-slider drag and persists the chosen colour to the server. */
    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof GunRefitScreen) || InteractiveRefitScreen.hasCards(event.getScreen())) return;
        if (draggingSlider == -1) return;
        draggingSlider = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        ItemStack laser = editableLaser(gun);
        MountPath path = focusedPath();
        if (laser.isEmpty() || path == null) return;
        NetworkHandler.CHANNEL.sendToServer(
                new ClientMessageSetRailLaserColor(path, LaserColorUtil.getLaserColor(laser)));
    }

    private static int railSlotX(int displayX, int index) {
        return displayX - (index + 1) * STEP;
    }

    // ---- Shared layout (so the underbarrel's own attachment row matches this one) -----------------

    /** The host-marker column: the right-anchored column every sub-slot row (rails, underbarrel) hangs from. */
    public static int anchorX(int screenWidth) {
        return screenWidth - 30;
    }

    /** Column x of sub-slot {@code index} in a row anchored at {@code anchorX} (stacking left of the marker). */
    public static int slotColumnX(int anchorX, int index) {
        return anchorX - (index + 1) * STEP;
    }

    /**
     * Y where the next sub-slot row (the underbarrel's) should sit: tucked just below the rail host rows, and
     * pushed further down only while a rail slot is in use (its unload/picker panel occupies that space) — so
     * the rows sit close together and "move apart" dynamically when a rail attachment is being handled.
     */
    public static int subRowBottomY(LocalPlayer player, ItemStack gunItem) {
        List<ScopeRails.RailHost> hosts = ScopeRails.getRailHosts(gunItem);
        if (hosts.isEmpty()) return ROW_Y0; // no rail rows — the underbarrel's row is the first sub-row
        int y = railBlockBottom(gunItem) + GROUP_GAP; // right below the rail rows (the panel's top)
        List<RailsModifier.RailSlot> slots = currentSlots(gunItem);
        if (selectedSlot >= 0 && selectedSlot < slots.size()) {
            ItemStack mounted = mountedAt(gunItem, selectedSlot);
            int panelHeight = mounted.isEmpty()
                    ? collectInventorySights(player, slots.get(selectedSlot), viewPath.hostType()).size() * SIZE
                    : (UNLOAD_SIZE + 2);
            y += panelHeight + GROUP_GAP;
        }
        return y;
    }

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

    private static boolean inSlot(double mouseX, double mouseY, int x, int y) {
        return inRect(mouseX, mouseY, x, y, SIZE, SIZE);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---- Laser colour sliders --------------------------------------------------------------------

    /** The focused mount if it is a colour-editable laser, else empty. */
    private static ItemStack editableLaser(ItemStack gun) {
        if (selectedSlot < 0) return ItemStack.EMPTY;
        ItemStack mounted = mountedAt(gun, selectedSlot);
        if (mounted.isEmpty()) return ItemStack.EMPTY;
        IAttachment attachment = IAttachment.getIAttachmentOrNull(mounted);
        if (attachment == null) return ItemStack.EMPTY;
        LaserConfig config = TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(mounted))
                .map(ClientAttachmentIndex::getLaserConfig).orElse(null);
        return (config != null && config.canEdit()) ? mounted : ItemStack.EMPTY;
    }

    private static MountPath focusedPath() {
        return selectedSlot < 0 ? null : viewPath.child(selectedSlot);
    }

    private static int sliderX() {
        return 20;
    }

    private static int hueY(GunRefitScreen screen) {
        return screen.height - 56;
    }

    private static int satY(GunRefitScreen screen) {
        return hueY(screen) + SLIDER_H + 6;
    }

    /** Live-updates the dragged slider from the mouse (dirty-writing the colour for preview) and draws both bars. */
    private static void handleAndRenderLaserSliders(GuiGraphics graphics, GunRefitScreen screen, ItemStack gun, int mouseX) {
        ItemStack laser = editableLaser(gun);
        if (laser.isEmpty()) {
            draggingSlider = -1;
            return;
        }
        int x = sliderX();
        int hueY = hueY(screen);
        int satY = satY(screen);

        int color = LaserColorUtil.getLaserColor(laser);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);

        if (draggingSlider == 0 || draggingSlider == 1) {
            hsb[draggingSlider] = clamp01((mouseX - x) / (float) (SLIDER_W - 1));
            applyRailLaserColor(gun, focusedPath(), Color.HSBtoRGB(hsb[0], hsb[1], 1f));
        }

        drawGradientBar(graphics, x, hueY, -1f);        // hue rainbow
        drawGradientBar(graphics, x, satY, hsb[0]);     // saturation for the current hue
        drawHandle(graphics, x, hueY, hsb[0]);
        drawHandle(graphics, x, satY, hsb[1]);
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("tooltip.renaissance_lib.rail.laser_color"), x, hueY - 10, 0xFFFFFFFF, false);
    }

    /** {@code true} and starts a drag if the press landed on a laser slider. */
    private static boolean laserSliderPress(GunRefitScreen screen, ItemStack gun, double mouseX, double mouseY) {
        if (editableLaser(gun).isEmpty()) return false;
        if (inRect(mouseX, mouseY, sliderX(), hueY(screen), SLIDER_W, SLIDER_H)) {
            draggingSlider = 0;
            return true;
        }
        if (inRect(mouseX, mouseY, sliderX(), satY(screen), SLIDER_W, SLIDER_H)) {
            draggingSlider = 1;
            return true;
        }
        return false;
    }

    /** Dirty-writes the laser colour onto the client gun's rail storage so the beam previews live. */
    private static void applyRailLaserColor(ItemStack gun, MountPath path, int rgb) {
        if (path == null) return;
        ItemStack laser = RailStorage.getMountedOnGun(gun, path);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(laser);
        if (attachment == null) return;
        attachment.setLaserColor(laser, rgb);
        RailStorage.setMounted(gun, path, laser);
    }

    /** A 1px-per-column gradient bar: hue rainbow when {@code hue < 0}, else saturation ramp for that hue. */
    private static void drawGradientBar(GuiGraphics graphics, int x, int y, float hue) {
        for (int i = 0; i < SLIDER_W; i++) {
            float t = i / (float) (SLIDER_W - 1);
            int rgb = (hue < 0f ? Color.HSBtoRGB(t, 1f, 1f) : Color.HSBtoRGB(hue, t, 1f)) | 0xFF000000;
            graphics.fill(x + i, y, x + i + 1, y + SLIDER_H, rgb);
        }
        graphics.renderOutline(x - 1, y - 1, SLIDER_W + 2, SLIDER_H + 2, 0xFF000000);
    }

    private static void drawHandle(GuiGraphics graphics, int x, int y, float value) {
        int hx = x + Math.round(clamp01(value) * (SLIDER_W - 1));
        graphics.fill(hx - 1, y - 2, hx + 2, y + SLIDER_H + 2, 0xFFFFFFFF);
        graphics.fill(hx, y - 1, hx + 1, y + SLIDER_H + 1, 0xFF000000);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Inventory slot indices holding an attachment accepted by {@code slot} on {@code hostType}. */
    public static List<Integer> collectInventorySights(LocalPlayer player, RailsModifier.RailSlot slot,
                                                       AttachmentType hostType) {
        List<Integer> result = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && result.size() < MAX_PICKER; i++) {
            ItemStack stack = inventory.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment != null && slotAccepts(slot, stack, attachment, hostType)) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Whether an attachment passes a slot's {@code allow_attachments} ids / tags and matches any of its {@code allow}
     * categories on a host of {@code hostType}.
     * Scope/sight are refined here with the client display flags ({@code isScope}/{@code isSight}); other
     * categories match by TaC:Z attachment type. Enforces optics-only-on-scope-hosts: a non-scope host never
     * accepts a SCOPE mount. An optic whose client index can't be resolved is treated as a scope (fail-open).
     */
    private static boolean slotAccepts(RailsModifier.RailSlot slot, ItemStack stack, IAttachment attachment,
                                       AttachmentType hostType) {
        AttachmentType type = attachment.getType(stack);
        if (hostType != AttachmentType.SCOPE && type == AttachmentType.SCOPE) return false;
        if (!slot.acceptsAttachment(attachment.getAttachmentId(stack))) return false;
        ClientAttachmentIndex index =
                TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).orElse(null);
        for (String category : slot.getAllow()) {
            if (RailsModifier.RailSlot.ALLOW_ANY.equals(category)) return true;
            if (RailsModifier.RailSlot.ALLOW_SCOPE.equals(category)) {
                if (type == AttachmentType.SCOPE && (index == null || index.isScope())) return true;
            } else if (RailsModifier.RailSlot.ALLOW_SIGHT.equals(category)) {
                if (type == AttachmentType.SCOPE && index != null && index.isSight() && !index.isScope()) return true;
            } else if (ScopeRails.categoryType(category) == type) {
                return true;
            }
        }
        return false;
    }
}
