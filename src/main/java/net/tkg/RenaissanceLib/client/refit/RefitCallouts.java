package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.ConversionStorage;
import net.tkg.RenaissanceLib.attachment.MountPath;
import net.tkg.RenaissanceLib.attachment.RailStorage;
import net.tkg.RenaissanceLib.attachment.RailsModifier;
import net.tkg.RenaissanceLib.attachment.ScopeRails;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.client.gui.ConversionRefitOverlay;
import net.tkg.RenaissanceLib.client.gui.RailRefitOverlay;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The floating slot cards of the interactive refit screen: one card per mount point — TaC:Z's slots the gun allows,
 * plus ours ({@link RefitSlot}): every rail mount (nested ones too), the installed underbarrel's own slots and the
 * conversion-kit slot, all visible at once (ours with a coloured edge). Each is placed just outside the gun on the
 * side its part faces (scope above, muzzle/stock at the ends, grip/mag below), joined to the part by a leader line
 * from a dot on its anchor ({@link RefitAnchors}, projected by {@link RefitProjection}). Cards on a side push apart
 * so they never overlap, glide instead of jumping as the gun turns, and dim when their part is on the far side.
 * Slots without an anchor stack in a dock, bottom-left. Selection lives in the screen; clicking is routed there.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitCallouts {
    private static final int CARD_H = 22;
    private static final int ICON = 18;
    private static final int MIN_W = 70, MAX_W = 140;
    /** Gap between the gun's on-screen outline and the cards, and between stacked cards. */
    private static final float GAP = 14f, SPACING = 4f, SCREEN_MARGIN = 6f;
    /** A card keeps its side until another side wins by this factor (stops flicker at the boundary). */
    private static final float SIDE_HYSTERESIS = 1.15f;
    private static final float EASE_TAU = 0.07f;
    /** Fade time constant (seconds) for cards stepping aside while another card is focused. */
    private static final float FADE_TAU = 0.08f;
    /** Below this presence a card isn't drawn at all. */
    private static final float MIN_PRESENCE = 0.02f;
    private static final int ACCENT = 0xFFD040;

    private enum Side { LEFT, RIGHT, TOP, BOTTOM }

    /** Edge tints for our own slots (the colours their old refit rows used); 0 = a TaC:Z slot, no tint. */
    private static final int TINT_RAIL = 0x66FF99, TINT_UNDERBARREL = 0xFFA500, TINT_CONVERSION = 0xFFD700;
    /** Rail mounts nested deeper than this aren't carded (mirrors RefitAnchors). */
    private static final int MAX_RAIL_DEPTH = 4;

    private static final class Card {
        final RefitSlot slot;
        AttachmentType iconType = AttachmentType.SCOPE;
        int tint;
        ItemStack attachment = ItemStack.EMPTY;
        String slotName = "", itemName = "";
        int w = MIN_W;
        @Nullable RefitProjection.Point anchor;
        boolean farSide, docked;
        Side side;
        float targetX, targetY, x, y;
        boolean placed = false;
        /** Whether the card is shown: all cards in the overview, only the focused one while a card is focused. */
        boolean shown = true;
        /** Eased 0..1 toward {@link #shown} — cards fade out on focus and back in on unfocus. */
        float presence = 1f;

        Card(RefitSlot slot) {
            this.slot = slot;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + CARD_H;
        }
    }

    /** One card's content for this frame. */
    private record Entry(RefitSlot slot, AttachmentType iconType, String name, ItemStack installed, int tint) {}

    private final Map<RefitSlot, Card> cards = new HashMap<>();
    private final List<Card> visible = new ArrayList<>();
    private long lastLayoutNanos = 0L;
    @Nullable
    private RefitSlot selected;
    /** Screen area the other cards move out of — the selected card's option list {x, y, w, h} — or null. */
    @Nullable
    private float[] keepClear;

    /**
     * Recompute cards and layout for this frame ({@code selected} = the focused slot, or {@code null} in the
     * overview). Call once per screen render, before {@link #draw}.
     */
    public void layout(@Nullable RefitSlot selected, @Nullable float[] keepClear) {
        this.selected = selected;
        this.keepClear = keepClear;
        visible.clear();
        Minecraft mc = Minecraft.getInstance();
        BedrockGunModel model = RefitProjection.model();
        ItemStack gun = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (model == null || iGun == null) return;
        Font font = mc.font;

        RefitProjection.Point centre = RefitProjection.project(RefitOrbit.pivot(model));
        float[] rect = gunRect(model, centre);

        List<Entry> entries = collect(mc, gun, iGun);
        cards.keySet().retainAll(entries.stream().map(Entry::slot).toList());
        for (Entry entry : entries) {
            Card card = cards.computeIfAbsent(entry.slot(), Card::new);
            card.iconType = entry.iconType();
            card.tint = entry.tint();
            card.attachment = entry.installed();
            card.slotName = entry.name();
            card.itemName = card.attachment.isEmpty() ? I18n.get("gui.renaissance_lib.refit.empty")
                    : card.attachment.getHoverName().getString();
            int textW = Math.max(font.width(card.slotName), font.width(card.itemName));
            card.w = Math.min(MAX_W, Math.max(MIN_W, ICON + 8 + textW + 4));
            card.anchor = RefitProjection.project(RefitAnchors.anchor(entry.slot(), model, gun));
            card.docked = card.anchor == null || centre == null;
            card.farSide = !card.docked && card.anchor.depth() > centre.depth() + 0.01f;
            // Focused on a card: the others step aside (fade out, no clicks) until the focus is dropped.
            card.shown = selected == null || card.slot.equals(selected);
            visible.add(card);
        }

        placeOnGun(centre, rect);
        placeDocked();
        ease();
    }

    /**
     * Every card this gun has right now: TaC:Z's slots the gun allows; every rail mount on every installed rail
     * host (nested mounts included); the installed underbarrel's own slots; and the conversion-kit slot when a kit
     * is installed or a compatible one is carried.
     */
    private static List<Entry> collect(Minecraft mc, ItemStack gun, IGun iGun) {
        List<Entry> entries = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE || !iGun.allowAttachmentType(gun, type)) continue;
            entries.add(new Entry(new RefitSlot.Native(type), type, slotName(type), iGun.getAttachment(gun, type), 0));
        }
        for (ScopeRails.RailHost host : ScopeRails.getRailHosts(gun)) {
            collectRails(entries, gun, MountPath.root(host.type()), host.spec());
        }
        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
        if (ubData != null) {
            for (AttachmentType type : UnderbarrelAttachments.getAllowedTypes(ubData)) {
                entries.add(new Entry(new RefitSlot.UnderbarrelSlot(type), type,
                        I18n.get("gui.renaissance_lib.refit.underbarrel_slot", slotName(type)),
                        UnderbarrelAttachments.getInstalled(gun, type), TINT_UNDERBARREL));
            }
        }
        if (mc.player != null && (ConversionStorage.hasKit(gun)
                || !ConversionRefitOverlay.collectInventoryKits(mc.player, gun).isEmpty())) {
            String key = "tooltip.renaissance_lib.conversion.slot";
            entries.add(new Entry(new RefitSlot.Conversion(), AttachmentType.EXTENDED_MAG,
                    I18n.exists(key) ? I18n.get(key) : "Conversion Kit", ConversionStorage.getKit(gun),
                    TINT_CONVERSION));
        }
        return entries;
    }

    private static void collectRails(List<Entry> entries, ItemStack gun, MountPath hostPath, RailsModifier.Spec spec) {
        if (spec == null || hostPath.depth() >= MAX_RAIL_DEPTH) return;
        List<RailsModifier.RailSlot> slots = spec.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            MountPath path = hostPath.child(i);
            ItemStack mounted = RailStorage.getMountedOnGun(gun, path);
            entries.add(new Entry(new RefitSlot.Rail(path), AttachmentType.SCOPE,
                    RailRefitOverlay.railSlotName(slots, i).getString(), mounted, TINT_RAIL));
            if (!mounted.isEmpty()) {
                collectRails(entries, gun, path, ScopeRails.getRailsSpecForAttachment(mounted));
            }
        }
    }

    /** The gun's on-screen outline (min x, min y, max x, max y) from its projected bounding box. */
    private static float[] gunRect(BedrockGunModel model, @Nullable RefitProjection.Point centre) {
        float[] r = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        RefitOrbit.Bounds box = RefitOrbit.bounds(model);
        if (box.min() != null) {
            Vector3f min = box.min(), max = box.max();
            for (int i = 0; i < 8; i++) {
                RefitProjection.Point p = RefitProjection.project(new Vector3f((i & 1) == 0 ? min.x : max.x,
                        (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z));
                if (p == null) continue; // project() already drops non-finite points
                r[0] = Math.min(r[0], p.x());
                r[1] = Math.min(r[1], p.y());
                r[2] = Math.max(r[2], p.x());
                r[3] = Math.max(r[3], p.y());
            }
        }
        if (r[0] > r[2] && centre != null) {
            r = new float[]{centre.x() - 40, centre.y() - 20, centre.x() + 40, centre.y() + 20};
        }
        return r;
    }

    private void placeOnGun(@Nullable RefitProjection.Point centre, float[] rect) {
        if (centre == null) return;
        float halfW = Math.max(1f, (rect[2] - rect[0]) * 0.5f), halfH = Math.max(1f, (rect[3] - rect[1]) * 0.5f);
        Map<Side, List<Card>> bySide = new EnumMap<>(Side.class);
        for (Card card : visible) {
            if (card.docked) continue;
            // Which side of the gun outline the part faces: the ray centre→part, normalised by the outline's size.
            float nx = (card.anchor.x() - centre.x()) / halfW, ny = (card.anchor.y() - centre.y()) / halfH;
            Side side = Math.abs(nx) >= Math.abs(ny) ? (nx < 0 ? Side.LEFT : Side.RIGHT) : (ny < 0 ? Side.TOP : Side.BOTTOM);
            if (card.side != null && card.side != side && strength(card.side, nx, ny) * SIDE_HYSTERESIS >= strength(side, nx, ny)) {
                side = card.side;
            }
            card.side = side;
            switch (side) {
                case LEFT -> {
                    card.targetX = rect[0] - GAP - card.w;
                    card.targetY = card.anchor.y() - CARD_H * 0.5f;
                }
                case RIGHT -> {
                    card.targetX = rect[2] + GAP;
                    card.targetY = card.anchor.y() - CARD_H * 0.5f;
                }
                case TOP -> {
                    card.targetX = card.anchor.x() - card.w * 0.5f;
                    card.targetY = rect[1] - GAP - CARD_H;
                }
                case BOTTOM -> {
                    card.targetX = card.anchor.x() - card.w * 0.5f;
                    card.targetY = rect[3] + GAP;
                }
            }
            bySide.computeIfAbsent(side, s -> new ArrayList<>()).add(card);
        }
        var window = Minecraft.getInstance().getWindow();
        float screenW = window.getGuiScaledWidth(), screenH = window.getGuiScaledHeight();
        for (Map.Entry<Side, List<Card>> entry : bySide.entrySet()) {
            boolean vertical = entry.getKey() == Side.LEFT || entry.getKey() == Side.RIGHT;
            spread(entry.getValue(), vertical, vertical ? screenH : screenW);
        }
        // Cards under the selected card's option list move out of it, then the sides re-space.
        if (keepClear != null && moveOutOf(keepClear, screenW, screenH)) {
            for (Map.Entry<Side, List<Card>> entry : bySide.entrySet()) {
                boolean vertical = entry.getKey() == Side.LEFT || entry.getKey() == Side.RIGHT;
                spread(entry.getValue(), vertical, vertical ? screenH : screenW);
            }
        }
        for (Card card : visible) {
            if (card.docked) continue;
            card.targetX = clamp(card.targetX, SCREEN_MARGIN, screenW - SCREEN_MARGIN - card.w);
            card.targetY = clamp(card.targetY, SCREEN_MARGIN, screenH - SCREEN_MARGIN - CARD_H);
        }
    }

    /**
     * Push every on-gun card (except the selected one — the list hangs from it) that overlaps {@code rect} out of it
     * by the shortest way that stays on screen: left, right, up or down. Returns whether anything moved.
     */
    private boolean moveOutOf(float[] rect, float screenW, float screenH) {
        float rx = rect[0] - SPACING, ry = rect[1] - SPACING, rr = rect[0] + rect[2] + SPACING,
                rb = rect[1] + rect[3] + SPACING;
        boolean moved = false;
        for (Card card : visible) {
            if (card.docked || !card.shown || card.slot.equals(selected)) continue;
            float x = card.targetX, y = card.targetY, r = x + card.w, b = y + CARD_H;
            if (r <= rx || x >= rr || b <= ry || y >= rb) continue; // clear already
            float toLeft = r - rx, toRight = rr - x, toUp = b - ry, toDown = rb - y;
            float best = Float.POSITIVE_INFINITY;
            float nx = x, ny = y;
            if (rx - card.w >= SCREEN_MARGIN && toLeft < best) { best = toLeft; nx = rx - card.w; ny = y; }
            if (rr + card.w <= screenW - SCREEN_MARGIN && toRight < best) { best = toRight; nx = rr; ny = y; }
            if (ry - CARD_H >= SCREEN_MARGIN && toUp < best) { best = toUp; nx = x; ny = ry - CARD_H; }
            if (rb + CARD_H <= screenH - SCREEN_MARGIN && toDown < best) { best = toDown; nx = x; ny = rb; }
            if (best == Float.POSITIVE_INFINITY) continue; // nowhere on screen to go — leave it
            card.targetX = nx;
            card.targetY = ny;
            moved = true;
        }
        return moved;
    }

    private static float strength(Side side, float nx, float ny) {
        return switch (side) {
            case LEFT -> -nx;
            case RIGHT -> nx;
            case TOP -> -ny;
            case BOTTOM -> ny;
        };
    }

    /**
     * Push the cards on one side apart along that side (y for left/right, x for top/bottom) so none overlap, keeping
     * them as close to their parts as possible, then shift the stack back on screen if it ran off the end.
     */
    private static void spread(List<Card> side, boolean vertical, float screenLength) {
        side.sort(Comparator.comparingDouble(c -> vertical ? c.targetY : c.targetX));
        for (int i = 1; i < side.size(); i++) {
            Card prev = side.get(i - 1), card = side.get(i);
            if (vertical) {
                card.targetY = Math.max(card.targetY, prev.targetY + CARD_H + SPACING);
            } else {
                card.targetX = Math.max(card.targetX, prev.targetX + prev.w + SPACING);
            }
        }
        if (side.isEmpty()) return;
        Card last = side.get(side.size() - 1);
        float overflow = vertical ? last.targetY + CARD_H - (screenLength - SCREEN_MARGIN)
                : last.targetX + last.w - (screenLength - SCREEN_MARGIN);
        if (overflow > 0) {
            for (Card card : side) {
                if (vertical) card.targetY -= overflow;
                else card.targetX -= overflow;
            }
        }
    }

    private void placeDocked() {
        float y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - SCREEN_MARGIN - CARD_H;
        for (int i = visible.size() - 1; i >= 0; i--) {
            Card card = visible.get(i);
            if (!card.docked) continue;
            card.side = null;
            card.targetX = SCREEN_MARGIN;
            card.targetY = y;
            y -= CARD_H + SPACING;
        }
    }

    /** Glide each card toward its target (snapping the first time it appears). */
    private void ease() {
        long now = System.nanoTime();
        float dt = lastLayoutNanos == 0L ? 1f : Math.min((now - lastLayoutNanos) / 1_000_000_000f, 0.1f);
        lastLayoutNanos = now;
        float alpha = 1f - (float) Math.exp(-dt / EASE_TAU);
        float fade = 1f - (float) Math.exp(-dt / FADE_TAU);
        for (Card card : visible) {
            float presenceTarget = card.shown ? 1f : 0f;
            card.presence = card.placed ? card.presence + (presenceTarget - card.presence) * fade : presenceTarget;
            if (!Float.isFinite(card.targetX) || !Float.isFinite(card.targetY)) continue; // never ease toward NaN
            // A card that ever went non-finite would stay NaN under easing (and draw at 0,0) — snap it back.
            if (!card.placed || !Float.isFinite(card.x) || !Float.isFinite(card.y)) {
                card.x = card.targetX;
                card.y = card.targetY;
                card.placed = true;
            } else {
                card.x += (card.targetX - card.x) * alpha;
                card.y += (card.targetY - card.y) * alpha;
            }
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    /** The on-screen rect {x, y, w, h} of a slot's card this frame, or {@code null} if it has none. */
    @Nullable
    public float[] cardRect(RefitSlot slot) {
        Card card = slot == null ? null : cards.get(slot);
        return card == null || !visible.contains(card) ? null : new float[]{card.x, card.y, card.w, CARD_H};
    }

    /** Whether {@code slot} has a card this frame (a selected slot can vanish, e.g. its rail host was removed). */
    public boolean has(RefitSlot slot) {
        Card card = slot == null ? null : cards.get(slot);
        return card != null && visible.contains(card);
    }

    /** The hovered card's attachment tooltip — drawn last, over everything. */
    public void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Card hovered = cardAt(mouseX, mouseY);
        if (hovered != null && !hovered.attachment.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, hovered.attachment, mouseX, mouseY);
        }
    }

    public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        if (visible.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ItemStack gun = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        RefitSlot selected = this.selected;
        Card hovered = cardAt(mouseX, mouseY);

        // Leader lines first, under the cards.
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Card card : visible) {
            if (card.docked || card.presence < MIN_PRESENCE) continue;
            int a = alpha(card, selected, hovered);
            float[] end = attachPoint(card);
            boolean lit = card == hovered || card.slot.equals(selected);
            int c = lit ? ACCENT : 0xE0E0E0;
            buf.vertex(pose, card.anchor.x(), card.anchor.y(), 0f).color(c >> 16 & 255, c >> 8 & 255, c & 255, a).endVertex();
            buf.vertex(pose, end[0], end[1], 0f).color(c >> 16 & 255, c >> 8 & 255, c & 255, a).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();

        for (Card card : visible) {
            if (card.presence < MIN_PRESENCE) continue;
            int a = alpha(card, selected, hovered);
            boolean lit = card == hovered || card.slot.equals(selected);
            if (!card.docked) {
                int ax = Math.round(card.anchor.x()), ay = Math.round(card.anchor.y());
                int dot = lit ? ACCENT : (card.tint != 0 ? card.tint : 0xFFFFFF);
                graphics.fill(ax - 2, ay - 2, ax + 2, ay + 2, (a << 24) | dot);
            }
            drawCard(graphics, font, gun, card, a, lit, card.slot.equals(selected));
        }
    }

    private static int alpha(Card card, @Nullable RefitSlot selected, @Nullable Card hovered) {
        int a = (card == hovered || card.slot.equals(selected)) ? 255 : (card.farSide ? 130 : 235);
        return Math.round(a * card.presence); // fading out while another card is focused
    }

    /** Where the leader line meets the card: the middle of the card edge facing the part. */
    private static float[] attachPoint(Card card) {
        if (card.side == null) return new float[]{card.x, card.y};
        return switch (card.side) {
            case LEFT -> new float[]{card.x + card.w, card.y + CARD_H * 0.5f};
            case RIGHT -> new float[]{card.x, card.y + CARD_H * 0.5f};
            case TOP -> new float[]{card.x + card.w * 0.5f, card.y + CARD_H};
            case BOTTOM -> new float[]{card.x + card.w * 0.5f, card.y};
        };
    }

    private static void drawCard(GuiGraphics graphics, Font font, ItemStack gun, Card card, int a, boolean lit,
                                 boolean selected) {
        int x = Math.round(card.x), y = Math.round(card.y), w = card.w;
        int bg = selected ? 0x2A2410 : 0x101418;
        graphics.fill(x, y, x + w, y + CARD_H, (Math.round(a * 0.78f) << 24) | bg);
        int border = (a << 24) | (lit ? ACCENT : 0x9A9A9A);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + CARD_H - 1, x + w, y + CARD_H, border);
        graphics.fill(x, y, x + 1, y + CARD_H, border);
        graphics.fill(x + w - 1, y, x + w, y + CARD_H, border);
        // Our own slots (rails, underbarrel, conversion) carry a coloured edge in their system's colour.
        if (card.tint != 0) graphics.fill(x + 1, y + 1, x + 3, y + CARD_H - 1, (a << 24) | card.tint);

        // Slot icon: TaC:Z's slot frame, then the installed attachment or the slot type's empty icon.
        int ix = x + 2, iy = y + 2;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, a / 255f);
        graphics.blit(GunRefitScreen.SLOT_TEXTURE, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
        if (card.attachment.isEmpty()) {
            int u = GunRefitScreen.getSlotTextureXOffset(gun, card.iconType);
            graphics.blit(GunRefitScreen.ICONS_TEXTURE, ix + 2, iy + 2, ICON - 4, ICON - 4, u, 0, 32, 32,
                    GunRefitScreen.getSlotsTextureWidth(), 32);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // Item icons ignore alpha — drop the icon once a stepping-aside card is half faded.
        if (!card.attachment.isEmpty() && card.presence > 0.5f) graphics.renderItem(card.attachment, ix + 1, iy + 1);

        int tx = x + ICON + 6, maxText = w - ICON - 10;
        graphics.drawString(font, font.plainSubstrByWidth(card.slotName, maxText), tx, y + 3,
                (a << 24) | (lit ? ACCENT : 0xAAAAAA), false);
        graphics.drawString(font, font.plainSubstrByWidth(card.itemName, maxText), tx, y + 12,
                (a << 24) | 0xFFFFFF, false);
    }

    // ---- input -------------------------------------------------------------------------------------------------

    @Nullable
    private Card cardAt(double mx, double my) {
        for (int i = visible.size() - 1; i >= 0; i--) {
            Card card = visible.get(i);
            if (card.shown && card.contains(mx, my)) return card; // stepped-aside cards take no hover or clicks
        }
        return null;
    }

    /** The slot under the mouse, or {@code null}. */
    @Nullable
    public RefitSlot slotAt(double mx, double my) {
        Card card = cardAt(mx, my);
        return card == null ? null : card.slot;
    }

    /** Forget layout state (placement, sides) — on open, so cards appear in place instead of gliding in. */
    public void reset() {
        cards.clear();
        visible.clear();
        lastLayoutNanos = 0L;
    }

    private static String slotName(AttachmentType type) {
        String key = "tooltip.tacz.attachment." + type.name().toLowerCase();
        if (I18n.exists(key)) return I18n.get(key);
        String alt = "tacz.type." + type.name().toLowerCase() + ".name";
        if (I18n.exists(alt)) return I18n.get(alt);
        String n = type.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static float clamp(float v, float lo, float hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
