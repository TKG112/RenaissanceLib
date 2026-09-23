package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The interactive refit screen (see {@code docs/dev/INTERACTIVE_REFIT_SCREEN.md}). It <em>extends</em> TaC:Z's
 * {@link GunRefitScreen} so every {@code instanceof GunRefitScreen} check in TaC:Z keeps working unchanged — the
 * refit camera ({@code RefitTransform}), hidden hotbar/crosshair, the refit key's toggle-close and the server's
 * post-install refresh (which re-runs {@link #init()}). {@code RefitKeyMixin} opens this instead of TaC:Z's.
 *
 * <p>Adds the turntable camera ({@link RefitOrbit}) — drag on empty space to rotate, right-drag to move the gun
 * across the screen, scroll to zoom, double-click or R to reset — the blurred background ({@link RefitBlur}), the
 * floating cards ({@link RefitCallouts}) for TaC:Z's slots and ours (rail mounts, the underbarrel's own slots, the
 * conversion kit — {@link RefitSlot}), replacing TaC:Z's slot buttons and our old refit-row overlays, with the
 * selected card's options as sub-cards below it ({@link RefitPicker}), and a P-toggled pivot/bounding-box debug
 * overlay ({@link RefitDebug}).
 */
@OnlyIn(Dist.CLIENT)
public class InteractiveRefitScreen extends GunRefitScreen {
    private static final long DOUBLE_CLICK_MS = 300L;

    /** Our screen was the last refit screen shown — its blur/orbit keep easing out after it closes. */
    private static boolean lingering = false;

    private final RefitCallouts callouts = new RefitCallouts();
    private final RefitPicker picker = new RefitPicker();
    /** The focused card (a TaC:Z slot or one of ours), or {@code null} in the overview. */
    @Nullable
    private RefitSlot selected;
    private boolean orbiting = false;
    private boolean panning = false;
    private long lastEmptyClickMs = 0L;

    public InteractiveRefitScreen() {
        super();
        RefitOrbit.reset(true); // every open starts from TaC:Z's default refit view (init() also runs on refresh)
        lingering = true;
    }

    /**
     * True while this screen is open, <em>and</em> after it closes until TaC:Z's refit closing transition
     * ({@code RefitTransform.getOpeningProgress()}) has run back to 0 — so the blur and the orbit fade out with the
     * camera instead of cutting off the moment the screen goes away. A plain TaC:Z refit screen ends it.
     */
    public static boolean isOpenOrClosing() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof InteractiveRefitScreen) return true;
        if (screen instanceof GunRefitScreen || RefitTransform.getOpeningProgress() <= 0f) lingering = false;
        return lingering;
    }

    /** TaC:Z's layout, minus its slot buttons — the floating cards replace them. */
    @Override
    public void init() {
        super.init();
        List<GuiEventListener> slotButtons = children().stream()
                .filter(GunAttachmentSlot.class::isInstance).map(GuiEventListener.class::cast).toList();
        slotButtons.forEach(this::removeWidget);
    }

    /**
     * Focus a card like TaC:Z's slot button does: the camera glides to the refit view of the native slot it lives on
     * ({@link RefitSlot#cameraType()} — a rail mount frames its host, the underbarrel's slots the grip) and the screen
     * rebuilds; focusing the focused card again goes back to the overview. For a TaC:Z slot, the rebuild is what fills
     * TaC:Z's attachment list for it; for ours the picker draws the options.
     */
    private void selectSlot(RefitSlot slot) {
        RefitSlot next = slot.equals(selected) ? null : slot;
        AttachmentType view = next == null ? AttachmentType.NONE : next.cameraType();
        if (RefitTransform.getCurrentTransformType() != view && !RefitTransform.changeRefitScreenView(view)) return;
        selected = next;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        init();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // TaC:Z's widgets first — the picker's options sit over the (faded) cards of other slots.
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && picker.click(mouseX, mouseY)) return true;
        RefitSlot card = callouts.slotAt(mouseX, mouseY);
        if (card != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectSlot(card);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            long now = System.currentTimeMillis();
            if (now - lastEmptyClickMs <= DOUBLE_CLICK_MS) {
                RefitOrbit.reset(false);
                lastEmptyClickMs = 0L;
            } else {
                lastEmptyClickMs = now;
            }
            orbiting = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && picker.dragLaser(mouseX)) return true;
        if (orbiting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            RefitOrbit.drag(dragX, dragY);
            return true;
        }
        if (panning && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            RefitOrbit.pan(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            orbiting = false;
            picker.releaseLaser();
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) return true;
        RefitOrbit.scroll(delta);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            RefitOrbit.reset(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_P) {
            RefitDebug.toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Layering: slot cards, then the picker's sub-card frames, then TaC:Z's own widgets (the picker's option icons,
     * repositioned) and tooltips, then the option names, then a hovered card's tooltip, then the debug overlay.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the focus honest: TaC:Z's view moved elsewhere, or the focused card vanished (e.g. its rail host or
        // the underbarrel was removed) — back to the overview.
        if (selected != null && RefitTransform.getCurrentTransformType() != selected.cameraType()) selected = null;
        // Cards keep clear of the option list (last frame's footprint — the list hangs from the selected card).
        callouts.layout(selected, selected == null ? null : picker.occupiedRect());
        if (selected != null && !callouts.has(selected)) {
            selected = null;
            if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) init();
        }
        picker.layout(children(), callouts, selected);
        callouts.draw(graphics, mouseX, mouseY);
        picker.drawBackgrounds(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        picker.drawLabels(graphics, mouseX, mouseY);
        callouts.drawTooltip(graphics, mouseX, mouseY);
        RefitDebug.draw(graphics);
    }
}
