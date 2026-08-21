package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentStatesModifier;
import net.tkg.RenaissanceLib.attachment.AttachmentToggleTargets;
import net.tkg.RenaissanceLib.attachment.FireAnimation;
import net.tkg.RenaissanceLib.attachment.FireAnimationModifier;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class AttachmentAnimationManager {

    private static final int TRACK = 0;

    private static final float TRANSITION_SECONDS = 0.15f;

    /** Shorter blend for a per-shot fire clip, so rapid fire retriggers it crisply rather than mushily. */
    private static final float FIRE_TRANSITION_SECONDS = 0.05f;

    private static final Map<ResourceLocation, AnimationController> CONTROLLERS = new HashMap<>();
    private static final Map<ResourceLocation, String> LAST_STATE = new HashMap<>();

    private static final Map<ResourceLocation, Boolean> UNAVAILABLE = new HashMap<>();

    private AttachmentAnimationManager() {}

    public static void tick(ItemStack gunItem) {
        if (IGun.getIGunOrNull(gunItem) == null) return;

        // Top-level slots. Both toggle states and fire-reaction animations play through the same per-attachment
        // controller; drive state changes here and advance the controller once so a fire clip (started by
        // onGunFire on the same controller) also progresses. A fire-only attachment has no states.
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;

            ResourceLocation attachmentId = attachmentId(gunItem, type);
            if (attachmentId == null) continue;

            AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, type);
            boolean hasStates = states != null && states.getAnimationFile() != null;
            FireAnimationModifier.Spec fire = FireAnimation.get(gunItem, type);
            if (!hasStates && fire == null) continue;

            String animationFile = hasStates ? states.getAnimationFile() : fire.getAnimationFile();
            AnimationController controller = getController(attachmentId, animationFile);
            if (controller == null) continue;

            if (hasStates) {
                reconcileStateToZoom(gunItem, type, states, attachmentId);
                runStateClipOnChange(controller, attachmentId, states, AttachmentStates.getState(gunItem, type));
            }
            controller.update();
        }

        // Rail-mounted optics: their own model animation plays through the same shared BedrockAttachmentModel
        // (per attachment id) that the rail sight renderer draws, so driving the controller here animates them.
        ActiveOptic active = ActiveOptic.resolve(gunItem);
        for (ToggleTarget target : AttachmentToggleTargets.railTargets(gunItem)) {
            ItemStack mounted = AttachmentToggleTargets.getItem(gunItem, target);
            AttachmentStatesModifier.States states = AttachmentStates.getStatesForItem(mounted);
            if (states == null || states.getAnimationFile() == null) continue;

            ResourceLocation attachmentId = idForItem(mounted);
            if (attachmentId == null) continue;

            AnimationController controller = getController(attachmentId, states.getAnimationFile());
            if (controller == null) continue;

            // Keep the sight's state in step with the shared zoom-key cycle, so cycling the view with the
            // zoom key plays the matching fold/deploy animation (mirrors reconcileStateToZoom for the scope).
            reconcileRailStateToZoom(gunItem, target, states, active);
            runStateClipOnChange(controller, attachmentId, states, AttachmentStates.getState(gunItem, target));
            controller.update();
        }
    }

    /**
     * Plays a fire-reaction clip on every installed attachment that declares a {@code fire_animation}, once
     * per host-gun shot. Runs on the same per-attachment controller/model as the toggle states, on the same
     * track, so it's the proven animation path — a fire animation and toggle states on one attachment share
     * that track (author them as separate attachments if you need both at once). Call on the client for the
     * local player's shot only.
     */
    public static void onGunFire(ItemStack gunItem) {
        if (IGun.getIGunOrNull(gunItem) == null) return;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;

            FireAnimationModifier.Spec fire = FireAnimation.get(gunItem, type);
            if (fire == null) continue;
            ResourceLocation attachmentId = attachmentId(gunItem, type);
            if (attachmentId == null) continue;

            AnimationController controller = getController(attachmentId, fire.getAnimationFile());
            if (controller == null) continue;

            String clip = fire.getClip();
            if (!controller.containPrototype(clip)) {
                RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] Attachment {} has no fire_animation clip '{}' in {}",
                        attachmentId, clip, fire.getAnimationFile());
                continue;
            }
            controller.runAnimation(TRACK, clip, ObjectAnimation.PlayType.PLAY_ONCE_STOP, FIRE_TRANSITION_SECONDS);
        }
    }

    /**
     * When a rail-mounted sight is the optic the zoom cycle currently points at, set its state to the one
     * whose {@code zoom_index} matches that active local view — so TaC:Z's zoom key drives the sight's state
     * (and thus its animation), not just the radial wheel. Derived from the synced zoom number each tick, so
     * it self-heals against item re-syncs.
     */
    private static void reconcileRailStateToZoom(ItemStack gunItem, ToggleTarget target,
                                                 AttachmentStatesModifier.States states, ActiveOptic active) {
        if (active == null || !states.isZoomKeyToggle()) return;
        if (!active.path.equals(target.path())) return;
        String zoomState = states.stateForZoomIndex(active.localIndex);
        if (zoomState != null && !zoomState.equals(AttachmentStates.getState(gunItem, target))) {
            AttachmentStates.setState(gunItem, target, zoomState);
        }
    }

    /**
     * Play the state's clip on the attachment's controller when its state changes. The controller (and its
     * model) are cached per attachment id and shared with rendering, so this animates both slot attachments
     * and rail-mounted optics. The caller advances the controller ({@code update()}) once per tick.
     */
    private static void runStateClipOnChange(AnimationController controller, ResourceLocation attachmentId,
                                             AttachmentStatesModifier.States states, String state) {
        String previous = LAST_STATE.get(attachmentId);
        if (!state.equals(previous)) {
            if (previous != null) {
                String clip = states.getAnimation(state);
                if (clip != null && !clip.isEmpty() && controller.containPrototype(clip)) {
                    controller.runAnimation(TRACK, clip, ObjectAnimation.PlayType.PLAY_ONCE_HOLD, TRANSITION_SECONDS);
                } else if (clip != null && !clip.isEmpty()) {
                    RenaissanceLibMod.LOGGER.warn(
                            "[RenaissanceLib] Attachment {} has no animation named '{}' in {}",
                            attachmentId, clip, states.getAnimationFile());
                }
            }
            LAST_STATE.put(attachmentId, state);
        }
    }

    @Nullable
    private static ResourceLocation idForItem(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        ResourceLocation id = iAttachment.getAttachmentId(attachmentItem);
        return (id == null || DefaultAssets.isEmptyAttachmentId(id)) ? null : id;
    }

    private static void reconcileStateToZoom(ItemStack gunItem, AttachmentType type,
                                             AttachmentStatesModifier.States states,
                                             ResourceLocation attachmentId) {
        if (type != AttachmentType.SCOPE) return;
        if (!states.isZoomKeyToggle()) return;

        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return;

        float[] zoom = TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> index.getZoom())
                .orElse(null);
        if (zoom == null || zoom.length == 0) return;

        CompoundTag scopeTag = iGun.getAttachmentTag(gunItem, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        int zoomIndex = Math.floorMod(zoomNumber, zoom.length);

        String zoomState = states.stateForZoomIndex(zoomIndex);
        if (zoomState != null && !zoomState.equals(AttachmentStates.getState(gunItem, type))) {
            AttachmentStates.setState(gunItem, type, zoomState);
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
    private static AnimationController getController(ResourceLocation attachmentId, String animationFile) {
        AnimationController existing = CONTROLLERS.get(attachmentId);
        if (existing != null) return existing;
        if (UNAVAILABLE.containsKey(attachmentId)) return null;

        try {
            ResourceLocation animationLocation = ResourceLocation.tryParse(animationFile);
            if (animationLocation == null) {
                RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Invalid animation_file '{}' on attachment {}",
                        animationFile, attachmentId);
                UNAVAILABLE.put(attachmentId, true);
                return null;
            }

            BedrockAttachmentModel model = TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .map(index -> index.getAttachmentModel())
                    .orElse(null);
            if (model == null) {

                return null;
            }

            BedrockAnimationFile animationFileData =
                    ClientAssetsManager.INSTANCE.getBedrockAnimations(animationLocation);
            if (animationFileData == null) {
                RenaissanceLibMod.LOGGER.error(
                        "[RenaissanceLib] Animation file not found for attachment {}: {} "
                                + "(expected assets/{}/animations/{}.animation.json)",
                        attachmentId, animationLocation,
                        animationLocation.getNamespace(), animationLocation.getPath());
                UNAVAILABLE.put(attachmentId, true);
                return null;
            }

            AnimationController controller = Animations.createControllerFromBedrock(animationFileData, model);
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
