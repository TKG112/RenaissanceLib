package net.tkg.RenaissanceLib.client;

import com.google.common.collect.Maps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ShaderManager {

    private static final Map<ResourceLocation, PostChain> SHADER_CACHE = Maps.newHashMap();
    private static ResourceLocation currentShader = null;
    private static PostChain        activeShader  = null;

    @Nullable
    public static PostChain loadShader(ResourceLocation shaderLocation) {
        if (SHADER_CACHE.containsKey(shaderLocation)) {
            return SHADER_CACHE.get(shaderLocation);
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation resourcePath = ResourceLocation.fromNamespaceAndPath(shaderLocation.getNamespace(), "shaders/post/" + shaderLocation.getPath() + ".json");

            if (!mc.getResourceManager().getResource(resourcePath).isPresent()) {
                return null;
            }

            PostChain postChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), ScopeShaderRenderer.getShaderTarget(), resourcePath);
            postChain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());

            SHADER_CACHE.put(shaderLocation, postChain);
            return postChain;

        } catch (Exception e) {
            return null;
        }
    }

    public static void activateShader(@Nullable ResourceLocation shaderLocation) {
        if (shaderLocation == null) {
            deactivateShader();
            return;
        }
        if (shaderLocation.equals(currentShader) && activeShader != null) {
            return;
        }
        PostChain shader = loadShader(shaderLocation);
        if (shader != null) {
            activeShader  = shader;
            currentShader = shaderLocation;

        }
    }

    public static void deactivateShader() {
        activeShader  = null;
        currentShader = null;
    }

    @Nullable
    public static PostChain getActiveShader() {
        return activeShader;
    }

    public static boolean isShaderActive() {
        return activeShader != null;
    }

    public static void resizeShaders(int width, int height) {

        ScopeShaderRenderer.resize(width, height);
        SHADER_CACHE.values().forEach(s -> s.resize(width, height));
    }

    public static void clearCache() {
        SHADER_CACHE.values().forEach(PostChain::close);
        SHADER_CACHE.clear();
        activeShader  = null;
        currentShader = null;
        ScopeShaderRenderer.destroySnapshot();
        ScopeShaderRenderer.destroyShaderTarget();

    }
}
