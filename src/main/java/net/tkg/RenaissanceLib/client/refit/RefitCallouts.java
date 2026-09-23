package net.tkg.RenaissanceLib.client.refit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The floating slot cards of the interactive refit screen (stage 3): one card per slot the gun allows, placed just
 * outside the gun on the side its part faces (scope above, muzzle/stock at the ends, grip/mag below), joined to the
 * part by a leader line from a dot on its mount point ({@link RefitOrbit#slotAnchor}, projected by
 * {@link RefitProjection}). Cards on a side push apart so they never overlap, glide instead of jumping as the gun
 * turns, and dim when their part is on the far side. Slots without a mount point stack in a dock, bottom-left.
 * Clicking a card selects the slot exactly like TaC:Z's slot button (camera to the slot's refit view + its
 * attachment list); clicking it again goes back to the overview.
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
    private static final int ACCENT = 0xFFD040;

    private enum Side { LEFT, RIGHT, TOP, BOTTOM }

    private static final class Card {
        final AttachmentType type;
        ItemStack attachment = ItemStack.EMPTY;
        String slotName = "", itemName = "";
        int w = MIN_W;
        @Nullable RefitProjection.Point anchor;
        boolean farSide, docked;
        Side side;
        float targetX, targetY, x, y;
        boolean placed = false;

        Card(AttachmentType type) {
            this.type = type;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + CARD_H;
        }
    }

    private final Map<AttachmentType, Card> cards = new EnumMap<>(AttachmentType.class);
    private final List<Card> visible = new ArrayList<>();
    private long lastLayoutNanos = 0L;

    /** Recompute cards and layout for this frame. Call once per screen render, before {@link #draw}. */
    public void layout() {
        visible.clear();
        Minecraft mc = Minecraft.getInstance();
        BedrockGunModel model = RefitProjection.model();
        ItemStack gun = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (model == null || iGun == null) return;
        Font font = mc.font;

        RefitProjection.Point centre = RefitProjection.project(RefitOrbit.pivot(model));
        float[] rect = gunRect(model, centre);

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE || !iGun.allowAttachmentType(gun, type)) {
                cards.remove(type);
                continue;
            }
            Card card = cards.computeIfAbsent(type, Card::new);
            card.attachment = iGun.getAttachment(gun, type);
            card.slotName = slotName(type);
            card.itemName = card.attachment.isEmpty() ? I18n.get("gui.renaissance_lib.refit.empty")
                    : card.attachment.getHoverName().getString();
            int textW = Math.max(font.width(card.slotName), font.width(card.itemName));
            card.w = Math.min(MAX_W, Math.max(MIN_W, ICON + 8 + textW + 4));
            card.anchor = RefitProjection.project(RefitOrbit.slotAnchor(model, type));
            card.docked = card.anchor == null || centre == null;
            card.farSide = !card.docked && card.anchor.depth() > centre.depth() + 0.01f;
            visible.add(card);
        }

        placeOnGun(centre, rect);
        placeDocked();
        ease();
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
                if (p == null) continue;
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
        for (Card card : visible) {
            if (card.docked) continue;
            card.targetX = clamp(card.targetX, SCREEN_MARGIN, screenW - SCREEN_MARGIN - card.w);
            card.targetY = clamp(card.targetY, SCREEN_MARGIN, screenH - SCREEN_MARGIN - CARD_H);
        }
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
        for (Card card : visible) {
            if (!card.placed) {
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

    public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        if (visible.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ItemStack gun = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        AttachmentType selected = RefitTransform.getCurrentTransformType();
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
            if (card.docked) continue;
            int a = alpha(card, selected, hovered);
            float[] end = attachPoint(card);
            boolean lit = card == hovered || card.type == selected;
            int c = lit ? ACCENT : 0xE0E0E0;
            buf.vertex(pose, card.anchor.x(), card.anchor.y(), 0f).color(c >> 16 & 255, c >> 8 & 255, c & 255, a).endVertex();
            buf.vertex(pose, end[0], end[1], 0f).color(c >> 16 & 255, c >> 8 & 255, c & 255, a).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.enableDepthTest();

        for (Card card : visible) {
            int a = alpha(card, selected, hovered);
            boolean lit = card == hovered || card.type == selected;
            if (!card.docked) {
                int ax = Math.round(card.anchor.x()), ay = Math.round(card.anchor.y());
                graphics.fill(ax - 2, ay - 2, ax + 2, ay + 2, (a << 24) | (lit ? ACCENT : 0xFFFFFF));
            }
            drawCard(graphics, font, gun, card, a, lit, card.type == selected);
        }

        if (hovered != null && !hovered.attachment.isEmpty()) {
            graphics.renderTooltip(font, hovered.attachment, mouseX, mouseY);
        }
    }

    private static int alpha(Card card, AttachmentType selected, @Nullable Card hovered) {
        if (card == hovered || card.type == selected) return 255;
        int a = card.farSide ? 130 : 235;
        if (selected != AttachmentType.NONE) a = Math.min(a, 90); // focused on another slot
        return a;
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

        // Slot icon: TaC:Z's slot frame, then the installed attachment or the slot type's empty icon.
        int ix = x + 2, iy = y + 2;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, a / 255f);
        graphics.blit(GunRefitScreen.SLOT_TEXTURE, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
        if (card.attachment.isEmpty()) {
            int u = GunRefitScreen.getSlotTextureXOffset(gun, card.type);
            graphics.blit(GunRefitScreen.ICONS_TEXTURE, ix + 2, iy + 2, ICON - 4, ICON - 4, u, 0, 32, 32,
                    GunRefitScreen.getSlotsTextureWidth(), 32);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (!card.attachment.isEmpty()) graphics.renderItem(card.attachment, ix + 1, iy + 1);

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
            if (visible.get(i).contains(mx, my)) return visible.get(i);
        }
        return null;
    }

    /** The slot under the mouse, or {@code null}. */
    @Nullable
    public AttachmentType slotAt(double mx, double my) {
        Card card = cardAt(mx, my);
        return card == null ? null : card.type;
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
