package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentStatesModifier;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class AttachmentAnimationManager {

    private static final int TRACK = 0;

    private static final float TRANSITION_SECONDS = 0.15f;

    private static final Map<ResourceLocation, AnimationController> CONTROLLERS = new HashMap<>();
    private static final Map<ResourceLocation, String> LAST_STATE = new HashMap<>();

    private static final Map<ResourceLocation, Boolean> UNAVAILABLE = new HashMap<>();

    private AttachmentAnimationManager() {}

    public static void tick(ItemStack gunItem) {
        if (IGun.getIGunOrNull(gunItem) == null) return;

        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;

            AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, type);
            if (states == null || states.getAnimationFile() == null) continue;

            ResourceLocation attachmentId = attachmentId(gunItem, type);
            if (attachmentId == null) continue;

            AnimationController controller = getController(attachmentId, states);
            if (controller == null) continue;

            String state = AttachmentStates.getState(gunItem, type);
            String previous = LAST_STATE.get(attachmentId);

            if (!state.equals(previous)) {
                String clip = states.getAnimation(state);
                if (clip != null && !clip.isEmpty() && controller.containPrototype(clip)) {

                    float transition = previous == null ? 0f : TRANSITION_SECONDS;

                    controller.runAnimation(TRACK, clip, ObjectAnimation.PlayType.PLAY_ONCE_HOLD, transition);
                } else if (clip != null && !clip.isEmpty()) {
                    RenaissanceLibMod.LOGGER.warn(
                            "[RenaissanceLib] Attachment {} has no animation named '{}' in {}",
                            attachmentId, clip, states.getAnimationFile());
                }
                LAST_STATE.put(attachmentId, state);
            }

            controller.update();
        }
    }

    @Nullable
    private static ResourceLocation attachmentId(ItemStack gunItem, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ResourceLocation id = iGun.getAttachmentId(gunItem, type);
        return (id == null || com.tacz.guns.api.DefaultAssets.isEmptyAttachmentId(id)) ? null : id;
    }

    @Nullable
    private static AnimationController getController(ResourceLocation attachmentId,
                                                     AttachmentStatesModifier.States states) {
        AnimationController existing = CONTROLLERS.get(attachmentId);
        if (existing != null) return existing;
        if (UNAVAILABLE.containsKey(attachmentId)) return null;

        try {
            ResourceLocation animationLocation = ResourceLocation.tryParse(states.getAnimationFile());
            if (animationLocation == null) {
                RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Invalid animation_file '{}' on attachment {}",
                        states.getAnimationFile(), attachmentId);
                UNAVAILABLE.put(attachmentId, true);
                return null;
            }

            BedrockAttachmentModel model = TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .map(index -> index.getAttachmentModel())
                    .orElse(null);
            if (model == null) {

                return null;
            }

            BedrockAnimationFile animationFile =
                    ClientAssetsManager.INSTANCE.getBedrockAnimations(animationLocation);
            if (animationFile == null) {
                RenaissanceLibMod.LOGGER.error(
                        "[RenaissanceLib] Animation file not found for attachment {}: {} "
                                + "(expected assets/{}/animations/{}.animation.json)",
                        attachmentId, animationLocation,
                        animationLocation.getNamespace(), animationLocation.getPath());
                UNAVAILABLE.put(attachmentId, true);
                return null;
            }

            AnimationController controller = Animations.createControllerFromBedrock(animationFile, model);
            CONTROLLERS.put(attachmentId, controller);
            RenaissanceLibMod.LOGGER.info("[RenaissanceLib] Loaded attachment animation for {} from {}",
                    attachmentId, animationLocation);
            return controller;
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to build animation controller for {}",
                    attachmentId, t);
            UNAVAILABLE.put(attachmentId, true);
            return null;
        }
    }

    public static void clearCache() {
        CONTROLLERS.clear();
        LAST_STATE.clear();
        UNAVAILABLE.clear();
    }
}
