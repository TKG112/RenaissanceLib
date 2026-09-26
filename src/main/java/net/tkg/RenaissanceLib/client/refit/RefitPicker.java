package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.LaserColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.tkg.RenaissanceLib.attachment.ConversionStorage;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.client.gui.ConversionRefitOverlay;
import net.tkg.RenaissanceLib.client.gui.RailRefitOverlay;
import net.tkg.RenaissanceLib.client.gui.UnderbarrelAttachmentRefitOverlay;
import net.tkg.RenaissanceLib.compat.TaczCompat;
import net.tkg.RenaissanceLib.network.ClientMessageSetConversionKit;
import net.tkg.RenaissanceLib.network.ClientMessageSetRailLaserColor;
import net.tkg.RenaissanceLib.network.ClientMessageSetRailSight;
import net.tkg.RenaissanceLib.network.ClientMessageSetUnderbarrelAttachment;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The attachment picker of the interactive refit screen: the options for the selected slot as sub-cards (icon +
 * name) in a single column just below the selected slot's card — flipping above it near the bottom of the screen —
 * with a square unload tile left of the first option when the slot has something installed. Its footprint
 * ({@link #occupiedRect()}) is what the other cards move out of.
 *
 * <p>For a <b>TaC:Z slot</b> it reuses TaC:Z's own list widgets: TaC:Z still builds the options
 * ({@code InventoryAttachmentSlot} — install-on-click, paging, tooltips, the beta's variant/slot-adapter buttons and
 * virtual entries, our {@code item_link} entries) and this only moves them into place each frame, widens each option
 * to its sub-card and draws the frame and name around it; page arrows go beside the rows, variant/adapter buttons and
 * TaC:Z's laser sliders underneath. TaC:Z's stats panel stays where it is.
 *
 * <p>For <b>our slots</b> (rail mounts, the underbarrel's own slots, the conversion kit) it draws its own sub-cards
 * the same way, filled from each system's inventory scan, and installs/removes through that system's existing
 * server-authoritative message — TaC:Z's list widgets are parked off-screen meanwhile. A colour-editable laser on a
 * rail mount gets hue/saturation bars under the options.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitPicker {
    /** Options stack in a single column under the card (user's layout). */
    private static final int SUB_W = 104, SUB_H = 20, GAP = 3, BELOW = 6, MARGIN = 6, MAX_PER_ROW = 1;
    private static final int ACCENT = 0xFFD040;
    /** TaC:Z's variant / slot-adapter button size (beta) — its "Show Diagrams" toggle is the same class, bigger. */
    private static final int VARIANT_W = 78, VARIANT_H = 12;
    /** Where TaC:Z draws the stats panel from (GunRefitScreen.render → GunPropertyDiagrams.draw at 11, 11). */
    private static final int STATS_PANEL_TOP = 11;
    private static final int SLIDER_W = 140, SLIDER_H = 10, SLIDER_BLOCK_H = 10 + SLIDER_H + 6 + SLIDER_H;
    private static final long INTERACT_COOLDOWN_MS = 250;
    private static final int OFF_SCREEN = -1000;

    /** A TaC:Z option widget laid out as a sub-card. */
    private record NativeOption(InventoryAttachmentSlot widget, int x, int y, ItemStack stack, String name) {
        boolean contains(double mx, double my) {
            return inRect(mx, my, x, y, SUB_W, SUB_H);
        }
    }

    /** One of our options: an inventory stack, installed via the selected slot's message. */
    private record OwnOption(int inventoryIndex, ItemStack stack, int x, int y, String name) {}

    private final List<NativeOption> nativeOptions = new ArrayList<>();
    private final List<OwnOption> ownOptions = new ArrayList<>();
    @Nullable
    private RefitSlot ownSlot;
    private boolean emptyNote = false;
    private int noteX, noteY;

    /** The unload tile: TaC:Z's button for its slots, our message for ours. */
    @Nullable
    private RefitUnloadButton nativeUnload;
    private boolean showUnload;
    private int unloadX, unloadY;

    /** Laser colour bars for a colour-editable laser on the selected rail mount. */
    @Nullable
    private MountPath laserPath;
    private int laserX, laserY;
    private int draggingBar = -1; // 0 = hue, 1 = saturation

    private long lastInteract = 0L;

    /** Screen area the picker covers this frame {x, y, w, h} (for the cards to keep clear of), or null. */
    @Nullable
    private float[] occupied;

    /** The area the picker covered last frame — other cards move out of it ({@link RefitCallouts}). */
    @Nullable
    public float[] occupiedRect() {
        return occupied;
    }

    // ---- layout ------------------------------------------------------------------------------------------------

    /** Lay out the picker for {@code selected} under its card. Call each frame after the cards' layout. */
    public void layout(List<? extends GuiEventListener> children, RefitCallouts callouts, @Nullable RefitSlot selected) {
        nativeOptions.clear();
        ownOptions.clear();
        ownSlot = null;
        emptyNote = false;
        nativeUnload = null;
        showUnload = false;
        laserPath = null;
        occupied = null;

        float[] card = selected == null ? null : callouts.cardRect(selected);
        boolean usable = card != null && Float.isFinite(card[0]) && Float.isFinite(card[1]);
        if (!usable || !selected.isNative()) parkNativeWidgets(children); // our slot (or nothing): TaC:Z's list hides
        if (!usable) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return;

        if (selected instanceof RefitSlot.Native n) {
            layoutNative(children, card, iGun.getAttachment(gun, n.type()).isEmpty());
        } else {
            layoutOwn(player, gun, selected, card);
        }
    }

    private void layoutNative(List<? extends GuiEventListener> children, float[] card, boolean slotEmpty) {
        List<InventoryAttachmentSlot> slots = new ArrayList<>();
        List<RefitTurnPageButton> pages = new ArrayList<>();
        List<AbstractWidget> extras = new ArrayList<>();
        RefitUnloadButton unload = null;
        for (GuiEventListener child : children) {
            if (child instanceof InventoryAttachmentSlot s) slots.add(s);
            else if (child instanceof RefitTurnPageButton p) pages.add(p);
            else if (child instanceof RefitUnloadButton u) unload = u;
            else if (child instanceof ForgeSlider slider) extras.add(slider); // TaC:Z's laser colour sliders
            else if (isVariantButton(child)) extras.add((AbstractWidget) child); // beta variant / slot adapter
        }
        if (unload != null && slotEmpty) { // unload only when something is installed
            park(unload);
            unload = null;
        }
        int extrasH = 0;
        for (AbstractWidget w : extras) extrasH += w.getHeight() + GAP;

        Grid g = grid(card, slots.size(), extrasH, unload != null, !pages.isEmpty());
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < slots.size(); i++) {
            InventoryAttachmentSlot widget = slots.get(i);
            int x = g.cellX(i), y = g.cellY(i);
            // TaC:Z's button stays its own 18x18 icon (it stretches its frame across its width, so widening it
            // breaks the texture); clicks on the rest of the sub-card are routed to it in click().
            widget.setX(x + 1);
            widget.setY(y + 1);
            ItemStack stack = TaczCompat.inventorySlotStack(widget);
            String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
            nativeOptions.add(new NativeOption(widget, x, y, stack, font.plainSubstrByWidth(name, SUB_W - 26)));
        }
        placeCommon(g, slots.isEmpty(), unload != null);
        if (unload != null) {
            nativeUnload = unload;
            unload.setX(unloadX + (SUB_H - 8) / 2); // TaC:Z's 8x8 button (icon + tooltip) centred in the tile
            unload.setY(unloadY + (SUB_H - 8) / 2);
        }
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setX(g.x0 + g.blockW + GAP);
            pages.get(i).setY(i == 0 ? g.y0 : g.y0 + g.blockH - 8);
        }
        int ey = g.y0 + g.blockH + GAP;
        int extrasW = 0;
        for (AbstractWidget w : extras) {
            w.setX(g.x0);
            w.setY(ey);
            ey += w.getHeight() + GAP;
            extrasW = Math.max(extrasW, w.getWidth());
        }
        occupy(g, Math.max(g.blockW, extrasW) + (pages.isEmpty() ? 0 : GAP + 18), ey - GAP);
    }

    /** Record the picker's footprint: from the unload tile (if any) to {@code width} past the column, down to {@code bottom}. */
    private void occupy(Grid g, int width, int bottom) {
        int left = showUnload ? unloadX : g.x0;
        occupied = new float[]{left, g.y0, g.x0 + width - left, Math.max(bottom, g.y0 + g.blockH) - g.y0};
    }

    private void layoutOwn(LocalPlayer player, ItemStack gun, RefitSlot slot, float[] card) {
        ownSlot = slot;
        ItemStack installed = installedIn(gun, slot);
        List<Integer> indices = installed.isEmpty() ? optionsFor(player, gun, slot) : List.of();
        if (slot instanceof RefitSlot.Rail r && editableLaser(gun, r.path()) != null) laserPath = r.path();

        Grid g = grid(card, indices.size(), laserPath != null ? SLIDER_BLOCK_H + GAP : 0, !installed.isEmpty(), false);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < indices.size(); i++) {
            ItemStack stack = player.getInventory().getItem(indices.get(i));
            ownOptions.add(new OwnOption(indices.get(i), stack, g.cellX(i), g.cellY(i),
                    font.plainSubstrByWidth(stack.getHoverName().getString(), SUB_W - 26)));
        }
        placeCommon(g, indices.isEmpty() && installed.isEmpty(), !installed.isEmpty());
        int bottom = g.y0 + g.blockH;
        int width = g.blockW;
        if (laserPath != null) {
            laserX = g.x0;
            laserY = g.y0 + g.blockH + GAP + 10; // below the label
            bottom = laserY + SLIDER_H + 6 + SLIDER_H + 2;
            width = Math.max(width, SLIDER_W + 2);
        }
        occupy(g, width, bottom);
    }

    /** Unload tile position (left of the first row) and the empty note. */
    private void placeCommon(Grid g, boolean noOptions, boolean unload) {
        showUnload = unload;
        if (unload) {
            unloadX = g.x0 - GAP - SUB_H;
            unloadY = g.y0;
        }
        if (noOptions) {
            emptyNote = true;
            noteX = g.x0;
            noteY = g.y0;
        }
    }

    /** Rows of sub-cards centred under the card (flipped above near the screen bottom), kept on screen. */
    private record Grid(int x0, int y0, int perRow, int blockW, int blockH) {
        int cellX(int i) {
            return x0 + (i % perRow) * (SUB_W + GAP);
        }

        int cellY(int i) {
            return y0 + (i / perRow) * (SUB_H + GAP);
        }
    }

    private static Grid grid(float[] card, int count, int extrasH, boolean unload, boolean pages) {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth(), screenH = window.getGuiScaledHeight();
        int perRow = Math.max(1, Math.min(MAX_PER_ROW, (screenW - 2 * MARGIN + GAP) / (SUB_W + GAP)));
        int cols = Math.max(1, Math.min(count, perRow));
        int rows = count == 0 ? 1 : (count + perRow - 1) / perRow;
        int blockW = count == 0 ? SUB_W + 40 : cols * SUB_W + (cols - 1) * GAP;
        int blockH = rows * SUB_H + (rows - 1) * GAP;
        int totalH = blockH + (extrasH > 0 ? GAP + extrasH : 0);
        int cardX = Math.round(card[0]), cardY = Math.round(card[1]), cardW = Math.round(card[2]),
                cardH = Math.round(card[3]);
        int left = MARGIN + (unload ? SUB_H + GAP : 0), right = screenW - MARGIN - blockW - (pages ? 18 + GAP : 0);
        int x0 = clamp(cardX + cardW / 2 - blockW / 2, left, right);
        int y0 = cardY + cardH + BELOW;
        if (y0 + totalH > screenH - MARGIN) y0 = cardY - BELOW - totalH; // no room below → flip above
        y0 = clamp(y0, MARGIN, screenH - MARGIN - totalH);
        return new Grid(x0, y0, perRow, blockW, blockH);
    }

    private static boolean isVariantButton(GuiEventListener child) {
        return isFlatColorButton(child) && ((AbstractWidget) child).getWidth() == VARIANT_W
                && ((AbstractWidget) child).getHeight() == VARIANT_H;
    }

    private static boolean isFlatColorButton(GuiEventListener child) {
        return child instanceof AbstractWidget w && "FlatColorButton".equals(w.getClass().getSimpleName());
    }

    /**
     * TaC:Z's stats area {x, y, w, h} — the "Show/Hide Diagrams" toggle (both versions: a FlatColorButton at
     * (11, 11) when hidden) plus, when shown, the stats panel TaC:Z draws from (11, 11) down to the toggle's top —
     * or null if there is no toggle. Its FlatColorButtons are the ones that aren't our repositioned variant buttons.
     */
    @Nullable
    public static float[] statsRect(List<? extends GuiEventListener> children) {
        float x = Float.POSITIVE_INFINITY, y = Float.POSITIVE_INFINITY, r = Float.NEGATIVE_INFINITY, b = Float.NEGATIVE_INFINITY;
        for (GuiEventListener child : children) {
            if (!isFlatColorButton(child) || isVariantButton(child)) continue;
            AbstractWidget w = (AbstractWidget) child;
            if (!w.visible) continue;
            x = Math.min(x, w.getX());
            y = Math.min(y, w.getY());
            r = Math.max(r, w.getX() + w.getWidth());
            b = Math.max(b, w.getY() + w.getHeight());
        }
        if (x > r) return null;
        y = Math.min(y, STATS_PANEL_TOP);
        return new float[]{x, y, r - x, b - y};
    }

    /** Move TaC:Z's list widgets out of the way (one of our slots is selected, or none). */
    private static void parkNativeWidgets(List<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof InventoryAttachmentSlot || child instanceof RefitTurnPageButton
                    || child instanceof RefitUnloadButton || child instanceof ForgeSlider || isVariantButton(child)) {
                park((AbstractWidget) child);
            }
        }
    }

    private static void park(AbstractWidget widget) {
        widget.setX(OFF_SCREEN);
        widget.setY(OFF_SCREEN);
    }

    // ---- our slots: content and actions ------------------------------------------------------------------------

    private static ItemStack installedIn(ItemStack gun, RefitSlot slot) {
        if (slot instanceof RefitSlot.Rail r) return RailStorage.getMountedOnGun(gun, r.path());
        if (slot instanceof RefitSlot.UnderbarrelSlot u) return UnderbarrelAttachments.getInstalled(gun, u.type());
        if (slot instanceof RefitSlot.Conversion) return ConversionStorage.getKit(gun);
        return ItemStack.EMPTY;
    }

    /** Inventory indices of the stacks that fit one of our slots (each system's own scan). */
    private static List<Integer> optionsFor(LocalPlayer player, ItemStack gun, RefitSlot slot) {
        if (slot instanceof RefitSlot.Rail r) {
            RailsModifier.RailSlot rail = railSlotAt(gun, r.path());
            return rail == null ? List.of()
                    : RailRefitOverlay.collectInventorySights(player, rail, r.path().hostType());
        }
        if (slot instanceof RefitSlot.UnderbarrelSlot u) {
            return UnderbarrelAttachmentRefitOverlay.collectInventory(player, u.type());
        }
        if (slot instanceof RefitSlot.Conversion) return ConversionRefitOverlay.collectInventoryKits(player, gun);
        return List.of();
    }

    /** The rail slot definition at {@code path}: on the host's spec at depth 1, else on the mount it hangs from. */
    @Nullable
    private static RailsModifier.RailSlot railSlotAt(ItemStack gun, MountPath path) {
        RailsModifier.Spec spec = path.depth() <= 1
                ? ScopeRails.getRailsSpecForType(gun, path.hostType())
                : ScopeRails.getRailsSpecForAttachment(RailStorage.getMountedOnGun(gun, path.parent()));
        int i = path.last();
        return spec == null || i < 0 || i >= spec.getSlots().size() ? null : spec.getSlots().get(i);
    }

    private static void sendInstall(RefitSlot slot, int inventoryIndex) {
        if (slot instanceof RefitSlot.Rail r) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetRailSight(r.path(), inventoryIndex));
        } else if (slot instanceof RefitSlot.UnderbarrelSlot u) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetUnderbarrelAttachment(u.type(), inventoryIndex));
        } else if (slot instanceof RefitSlot.Conversion) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetConversionKit(inventoryIndex));
        }
    }

    private static void sendUnload(RefitSlot slot) {
        if (slot instanceof RefitSlot.Rail r) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetRailSight(r.path(), ClientMessageSetRailSight.CLEAR));
        } else if (slot instanceof RefitSlot.UnderbarrelSlot u) {
            NetworkHandler.CHANNEL.sendToServer(
                    new ClientMessageSetUnderbarrelAttachment(u.type(), ClientMessageSetUnderbarrelAttachment.CLEAR));
        } else if (slot instanceof RefitSlot.Conversion) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetConversionKit(ClientMessageSetConversionKit.CLEAR));
        }
    }

    // ---- input -------------------------------------------------------------------------------------------------

    /** Handle a left click on the picker (options, unload, laser bars). Returns true if it was used. */
    public boolean click(double mx, double my) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (laserPath != null && pressLaser(mx, my)) return true;
        if (showUnload && inRect(mx, my, unloadX, unloadY, SUB_H, SUB_H)) {
            if (nativeUnload != null) {
                nativeUnload.onPress();
            } else if (ownSlot != null && ready()) {
                ItemStack installed = installedIn(player.getMainHandItem(), ownSlot);
                clickSound();
                if (!installed.isEmpty()) SoundPlayManager.playerRefitSound(installed, player, SoundManager.UNINSTALL_SOUND);
                sendUnload(ownSlot);
            }
            return true;
        }
        // TaC:Z options: a click on the icon itself already went to TaC:Z's button (the screen tries widgets
        // first); anywhere else on the sub-card presses the same button.
        for (NativeOption o : nativeOptions) {
            if (o.contains(mx, my)) {
                clickSound();
                o.widget().onPress();
                return true;
            }
        }
        for (OwnOption o : ownOptions) {
            if (inRect(mx, my, o.x(), o.y(), SUB_W, SUB_H)) {
                if (ownSlot != null && ready()) {
                    clickSound();
                    SoundPlayManager.playerRefitSound(o.stack(), player, SoundManager.INSTALL_SOUND);
                    sendInstall(ownSlot, o.inventoryIndex());
                }
                return true;
            }
        }
        return false;
    }

    private boolean ready() {
        long now = System.currentTimeMillis();
        if (now - lastInteract < INTERACT_COOLDOWN_MS) return false;
        lastInteract = now;
        return true;
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ---- rail laser colour -------------------------------------------------------------------------------------

    /** The mount at {@code path} if it's a colour-editable laser, else {@code null}. */
    @Nullable
    private static ItemStack editableLaser(ItemStack gun, MountPath path) {
        ItemStack mounted = RailStorage.getMountedOnGun(gun, path);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(mounted);
        if (attachment == null) return null;
        LaserConfig config = TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(mounted))
                .map(ClientAttachmentIndex::getLaserConfig).orElse(null);
        return config != null && config.canEdit() ? mounted : null;
    }

    private int hueY() {
        return laserY;
    }

    private int satY() {
        return laserY + SLIDER_H + 6;
    }

    private boolean pressLaser(double mx, double my) {
        if (inRect(mx, my, laserX, hueY(), SLIDER_W, SLIDER_H)) draggingBar = 0;
        else if (inRect(mx, my, laserX, satY(), SLIDER_W, SLIDER_H)) draggingBar = 1;
        else return false;
        dragLaser(mx);
        return true;
    }

    /** While a laser bar is held: live-preview the colour on the client gun. Returns true if dragging. */
    public boolean dragLaser(double mx) {
        if (draggingBar < 0 || laserPath == null) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack gun = player.getMainHandItem();
        ItemStack laser = editableLaser(gun, laserPath);
        IAttachment attachment = laser == null ? null : IAttachment.getIAttachmentOrNull(laser);
        if (attachment == null) return false;
        int color = LaserColorUtil.getLaserColor(laser);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hsb[draggingBar] = clamp01((float) (mx - laserX) / (SLIDER_W - 1));
        attachment.setLaserColor(laser, Color.HSBtoRGB(hsb[0], hsb[1], 1f));
        RailStorage.setMounted(gun, laserPath, laser);
        return true;
    }

    /** Releasing a laser bar persists the colour to the server. Returns true if a bar was being dragged. */
    public boolean releaseLaser() {
        if (draggingBar < 0) return false;
        draggingBar = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || laserPath == null) return true;
        ItemStack laser = editableLaser(player.getMainHandItem(), laserPath);
        if (laser != null) {
            NetworkHandler.CHANNEL.sendToServer(
                    new ClientMessageSetRailLaserColor(laserPath, LaserColorUtil.getLaserColor(laser)));
        }
        return true;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    /** Sub-card frames (and our own options' icons), under TaC:Z's widgets. */
    public void drawBackgrounds(GuiGraphics graphics, int mouseX, int mouseY) {
        for (NativeOption o : nativeOptions) {
            frame(graphics, o.x(), o.y(), SUB_W, SUB_H, o.contains(mouseX, mouseY));
        }
        for (OwnOption o : ownOptions) {
            frame(graphics, o.x(), o.y(), SUB_W, SUB_H, inRect(mouseX, mouseY, o.x(), o.y(), SUB_W, SUB_H));
            graphics.renderItem(o.stack(), o.x() + 2, o.y() + 2);
        }
        if (emptyNote) frame(graphics, noteX, noteY, SUB_W + 40, SUB_H, false);
        if (showUnload) {
            frame(graphics, unloadX, unloadY, SUB_H, SUB_H, inRect(mouseX, mouseY, unloadX, unloadY, SUB_H, SUB_H));
            if (nativeUnload == null) { // our slots: draw TaC:Z's unload icon ourselves
                boolean hover = inRect(mouseX, mouseY, unloadX, unloadY, SUB_H, SUB_H);
                graphics.blit(com.tacz.guns.client.gui.GunRefitScreen.UNLOAD_TEXTURE, unloadX + 6, unloadY + 6, 8, 8,
                        hover ? 80f : 0f, 0f, 80, 80, 160, 80);
            }
        }
        if (laserPath != null) drawLaserBars(graphics);
    }

    /** Names beside the icons, over TaC:Z's widgets; our options' tooltips. */
    public void drawLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        ItemStack tooltip = null;
        for (NativeOption o : nativeOptions) {
            boolean hover = o.contains(mouseX, mouseY);
            // Over the icon TaC:Z's button draws its own tooltip; over the rest of the sub-card, we do.
            if (hover && !o.widget().isMouseOver(mouseX, mouseY) && !o.stack().isEmpty()) tooltip = o.stack();
            graphics.drawString(font, o.name(), o.x() + 23, o.y() + 6, hover ? 0xFF000000 | ACCENT : 0xFFFFFFFF, false);
        }
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        OwnOption hovered = null;
        for (OwnOption o : ownOptions) {
            boolean hover = inRect(mouseX, mouseY, o.x(), o.y(), SUB_W, SUB_H);
            if (hover) hovered = o;
            graphics.drawString(font, o.name(), o.x() + 23, o.y() + 6, hover ? 0xFF000000 | ACCENT : 0xFFFFFFFF, false);
        }
        if (emptyNote) {
            graphics.drawString(font, I18n.get("gui.renaissance_lib.refit.no_attachments"), noteX + 6, noteY + 6,
                    0xFFAAAAAA, false);
        }
        if (hovered != null) graphics.renderTooltip(font, hovered.stack(), mouseX, mouseY);
    }

    private void drawLaserBars(GuiGraphics graphics) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack laser = player == null ? null : editableLaser(player.getMainHandItem(), laserPath);
        if (laser == null) return;
        int color = LaserColorUtil.getLaserColor(laser);
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        graphics.drawString(Minecraft.getInstance().font, I18n.get("tooltip.renaissance_lib.rail.laser_color"),
                laserX, laserY - 10, 0xFFFFFFFF, false);
        gradientBar(graphics, laserX, hueY(), -1f);
        gradientBar(graphics, laserX, satY(), hsb[0]);
        handle(graphics, laserX, hueY(), hsb[0]);
        handle(graphics, laserX, satY(), hsb[1]);
    }

    /** A 1px-per-column bar: the hue rainbow when {@code hue < 0}, else the saturation ramp for that hue. */
    private static void gradientBar(GuiGraphics graphics, int x, int y, float hue) {
        for (int i = 0; i < SLIDER_W; i++) {
            float t = i / (float) (SLIDER_W - 1);
            int rgb = (hue < 0f ? Color.HSBtoRGB(t, 1f, 1f) : Color.HSBtoRGB(hue, t, 1f)) | 0xFF000000;
            graphics.fill(x + i, y, x + i + 1, y + SLIDER_H, rgb);
        }
        graphics.renderOutline(x - 1, y - 1, SLIDER_W + 2, SLIDER_H + 2, 0xFF000000);
    }

    private static void handle(GuiGraphics graphics, int x, int y, float value) {
        int hx = x + Math.round(clamp01(value) * (SLIDER_W - 1));
        graphics.fill(hx - 1, y - 2, hx + 2, y + SLIDER_H + 2, 0xFFFFFFFF);
        graphics.fill(hx, y - 1, hx + 1, y + SLIDER_H + 1, 0xFF000000);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, boolean hover) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xD02A2410 : 0xC0101418);
        int border = hover ? 0xFF000000 | ACCENT : 0xFF6A6A6A;
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int clamp(int v, int lo, int hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
