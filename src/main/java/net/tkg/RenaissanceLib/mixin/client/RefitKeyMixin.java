package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.input.RefitKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.refit.InteractiveRefitScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Opens our {@link InteractiveRefitScreen} instead of TaC:Z's refit screen — the refit key's single
 * {@code new GunRefitScreen()} (same on the release and the beta). It subclasses TaC:Z's screen, so the key's own
 * toggle-close ({@code instanceof GunRefitScreen}) still works.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RefitKey.class, remap = false)
public class RefitKeyMixin {

    @Redirect(method = "onRefitPress",
            at = @At(value = "NEW", target = "()Lcom/tacz/guns/client/gui/GunRefitScreen;"),
            remap = false)
    private static GunRefitScreen renaissance$openInteractiveRefit() {
        return new InteractiveRefitScreen();
    }
}
