package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.tkg.RenaissanceLib.compat.TaczCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * The attachment picker of the interactive refit screen (stage 4): the options for the selected slot as sub-cards
 * (icon + name) in rows just below the selected slot card — flipping above it near the bottom of the screen.
 *
 * <p>It <em>reuses TaC:Z's own list widgets</em> rather than rebuilding the list: TaC:Z still builds the options
 * ({@code InventoryAttachmentSlot}, with its install-on-click, paging, tooltips, the beta's variant/slot-adapter
 * buttons and virtual entries, and our {@code item_link} entries), and this only moves them into place every frame,
 * widens each option to its sub-card, and draws the card frame and name around it. The unload button goes on the
 * selected card's corner; page arrows beside the rows; the beta's variant/adapter buttons and the laser colour
 * sliders underneath.
 */
@OnlyIn(Dist.CLIENT)
public final class RefitPicker {
    private static final int SUB_W = 104, SUB_H = 20, GAP = 3, BELOW = 6, MARGIN = 6, MAX_PER_ROW = 4;
    private static final int ACCENT = 0xFFD040;

    private record Option(InventoryAttachmentSlot widget, int x, int y, String name) {}

    private final List<Option> options = new ArrayList<>();
    private boolean emptyNote = false;
    private int noteX, noteY;

    /** Position TaC:Z's list widgets under the selected slot's card. Call each frame after the cards' layout. */
    public void layout(List<? extends GuiEventListener> children, RefitCallouts callouts) {
        options.clear();
        emptyNote = false;
        AttachmentType selected = RefitTransform.getCurrentTransformType();
        if (selected == AttachmentType.NONE) return;
        float[] card = callouts.cardRect(selected);
        if (card == null) return;

        List<InventoryAttachmentSlot> slots = new ArrayList<>();
        List<RefitTurnPageButton> pages = new ArrayList<>();
        List<AbstractWidget> extras = new ArrayList<>();
        RefitUnloadButton unload = null;
        for (GuiEventListener child : children) {
            if (child instanceof InventoryAttachmentSlot s) slots.add(s);
            else if (child instanceof RefitTurnPageButton p) pages.add(p);
            else if (child instanceof RefitUnloadButton u) unload = u;
            else if (child instanceof ForgeSlider slider) extras.add(slider);            // laser colour
            else if (child instanceof AbstractWidget w
                    && "FlatColorButton".equals(w.getClass().getSimpleName())) extras.add(w); // beta variant/adapter
        }

        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth(), screenH = window.getGuiScaledHeight();
        int perRow = Math.max(1, Math.min(MAX_PER_ROW, (screenW - 2 * MARGIN + GAP) / (SUB_W + GAP)));
        int count = slots.size();
        int cols = Math.max(1, Math.min(count, perRow));
        int rows = count == 0 ? 1 : (count + perRow - 1) / perRow;
        int blockW = count == 0 ? SUB_W + 40 : cols * SUB_W + (cols - 1) * GAP;
        int extrasH = 0;
        for (AbstractWidget w : extras) extrasH += w.getHeight() + GAP;
        int totalH = rows * SUB_H + (rows - 1) * GAP + (extrasH > 0 ? GAP + extrasH : 0);

        int cardX = Math.round(card[0]), cardY = Math.round(card[1]), cardW = Math.round(card[2]),
                cardH = Math.round(card[3]);
        int pageW = pages.isEmpty() ? 0 : 18 + GAP;
        int x0 = clamp(cardX + cardW / 2 - blockW / 2, MARGIN, screenW - MARGIN - blockW - pageW);
        int y0 = cardY + cardH + BELOW;
        if (y0 + totalH > screenH - MARGIN) y0 = cardY - BELOW - totalH;   // no room below → flip above
        y0 = clamp(y0, MARGIN, screenH - MARGIN - totalH);

        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < count; i++) {
            InventoryAttachmentSlot widget = slots.get(i);
            int x = x0 + (i % perRow) * (SUB_W + GAP), y = y0 + (i / perRow) * (SUB_H + GAP);
            widget.setX(x + 1);
            widget.setY(y + 1);
            widget.setWidth(SUB_W - 2); // the whole sub-card is clickable
            ItemStack stack = TaczCompat.inventorySlotStack(widget);
            String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
            options.add(new Option(widget, x, y, font.plainSubstrByWidth(name, SUB_W - 26)));
        }
        if (count == 0) {
            emptyNote = true;
            noteX = x0;
            noteY = y0;
        }

        // Page arrows to the right of the rows (up at the top, down at the bottom).
        int blockH = rows * SUB_H + (rows - 1) * GAP;
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setX(x0 + blockW + GAP);
            pages.get(i).setY(i == 0 ? y0 : y0 + blockH - 8);
        }
        // Unload on the selected card's top-right corner.
        if (unload != null) {
            unload.setX(cardX + cardW - 9);
            unload.setY(cardY + 1);
        }
        // Variant / adapter buttons and laser sliders stacked under the options.
        int ey = y0 + blockH + GAP;
        for (AbstractWidget w : extras) {
            w.setX(x0);
            w.setY(ey);
            ey += w.getHeight() + GAP;
        }
    }

    /** Sub-card frames, under TaC:Z's widgets. */
    public void drawBackgrounds(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Option o : options) {
            boolean hover = o.widget().isMouseOver(mouseX, mouseY);
            frame(graphics, o.x(), o.y(), SUB_W, SUB_H, hover);
        }
        if (emptyNote) frame(graphics, noteX, noteY, SUB_W + 40, SUB_H, false);
    }

    /** Names beside the icons, over TaC:Z's widgets. */
    public void drawLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        for (Option o : options) {
            boolean hover = o.widget().isMouseOver(mouseX, mouseY);
            graphics.drawString(font, o.name(), o.x() + 23, o.y() + 6, hover ? 0xFF000000 | ACCENT : 0xFFFFFFFF, false);
        }
        if (emptyNote) {
            graphics.drawString(font, I18n.get("gui.renaissance_lib.refit.no_attachments"), noteX + 6, noteY + 6,
                    0xFFAAAAAA, false);
        }
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, boolean hover) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xD02A2410 : 0xC0101418);
        int border = hover ? 0xFF000000 | ACCENT : 0xFF6A6A6A;
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private static int clamp(int v, int lo, int hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
