package net.tkg.RenaissanceLib.client.refit;

import com.tacz.guns.client.gui.GunRefitScreen;
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
 * space to rotate, scroll to zoom, double-click or R to reset — and the blurred background ({@link RefitBlur}).
 */
@OnlyIn(Dist.CLIENT)
public class InteractiveRefitScreen extends GunRefitScreen {
    private static final long DOUBLE_CLICK_MS = 300L;

    private boolean orbiting = false;
    private long lastEmptyClickMs = 0L;

    public InteractiveRefitScreen() {
        super();
        RefitOrbit.reset(true); // every open starts from TaC:Z's default refit view (init() also runs on refresh)
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
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (orbiting && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            RefitOrbit.drag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) orbiting = false;
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
