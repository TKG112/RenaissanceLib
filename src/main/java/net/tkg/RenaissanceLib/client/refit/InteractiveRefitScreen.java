package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The interactive refit screen (see {@code docs/dev/INTERACTIVE_REFIT_SCREEN.md}). It <em>extends</em> TaC:Z's
 * {@link GunRefitScreen} so every {@code instanceof GunRefitScreen} check in TaC:Z keeps working unchanged — the
 * refit camera ({@code RefitTransform}), hidden hotbar/crosshair, the refit key's toggle-close and the server's
 * post-install refresh (which re-runs {@link #init()}). {@code RefitKeyMixin} opens this instead of TaC:Z's.
 *
 * <p>Stage 1: TaC:Z's own buttons are kept; this adds the turntable camera ({@link RefitOrbit}) — drag on empty
 * space to rotate, right-drag to move the gun across the screen, scroll to zoom, double-click or R to reset — the
 * blurred background ({@link RefitBlur}), and a P-toggled pivot/bounding-box debug overlay ({@link RefitDebug}).
 */
@OnlyIn(Dist.CLIENT)
public class InteractiveRefitScreen extends GunRefitScreen {
    private static final long DOUBLE_CLICK_MS = 300L;

    /** Our screen was the last refit screen shown — its blur/orbit keep easing out after it closes. */
    private static boolean lingering = false;

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true; // a TaC:Z button took it
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) orbiting = false;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        RefitDebug.draw(graphics);
    }
}
