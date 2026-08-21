package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public class ScopeStateTracker {
    private static boolean holdingScopeWithShader = false;
    private static ResourceLocation currentScopeShader = null;

    public static boolean ENABLE_SCOPE_SHADERS = true;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        if (!ENABLE_SCOPE_SHADERS) {
            updateState(false, null);
            return;
        }

        if (mc.player == null) {
            updateState(false, null);
            return;
        }

        ItemStack mainHand = mc.player.getMainHandItem();

        IGun iGun = IGun.getIGunOrNull(mainHand);
        if (iGun == null) {
            updateState(false, null);
            return;
        }

        ResourceLocation scopeId = iGun.getAttachmentId(mainHand, AttachmentType.SCOPE);

        if (DefaultAssets.EMPTY_ATTACHMENT_ID.equals(scopeId)) {
            updateState(false, null);
            return;
        }

        ResourceLocation shader = ScopeShaderStorage.getShader(scopeId, getCurrentViewIndex(mainHand, iGun, scopeId));
        updateState(shader != null, shader);
    }

    public static int getCurrentViewIndex(ItemStack gunItem, IGun iGun, ResourceLocation scopeId) {
        try {
            int[] views = TimelessAPI.getClientAttachmentIndex(scopeId)
                    .map(ClientAttachmentIndex::getViews)
                    .orElse(null);
            if (views == null || views.length == 0) return 0;

            CompoundTag scopeTag = iGun.getAttachmentTag(gunItem, AttachmentType.SCOPE);
            int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
            return views[Math.floorMod(zoomNumber, views.length)] - 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void updateState(boolean holding, @Nullable ResourceLocation shader) {
        boolean stateChanged  = holdingScopeWithShader != holding;
        boolean shaderChanged = (currentScopeShader == null) != (shader == null)
                || (currentScopeShader != null && !currentScopeShader.equals(shader));

        if (!stateChanged && !shaderChanged) return;

        holdingScopeWithShader = holding;
        currentScopeShader = shader;

        if (holding && shader != null) {
            ShaderManager.activateShader(shader);
        } else {
            ShaderManager.deactivateShader();
        }
    }

    public static boolean isHoldingScopeWithShader() {
        return holdingScopeWithShader;
    }

    public static final float AIM_THRESHOLD = 0.01f;

    public static float getAimingProgress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        try {
            return IGunOperator.fromLivingEntity(mc.player).getSynAimingProgress();
        } catch (Throwable t) {

            return 1f;
        }
    }

    public static boolean isAimingThroughScope() {
        return getAimingProgress() >= AIM_THRESHOLD;
    }

    @Nullable
    public static ResourceLocation getCurrentScopeShader() {
        return currentScopeShader;
    }
}
