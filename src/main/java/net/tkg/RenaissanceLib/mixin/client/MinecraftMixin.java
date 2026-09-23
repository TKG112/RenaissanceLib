package net.tkg.RenaissanceLib.mixin.client;

import net.minecraft.client.Minecraft;
import net.tkg.RenaissanceLib.client.ShaderManager;
import net.tkg.RenaissanceLib.client.refit.RefitBlur;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void renaissance$onResize(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ShaderManager.resizeShaders(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        RefitBlur.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }
}
