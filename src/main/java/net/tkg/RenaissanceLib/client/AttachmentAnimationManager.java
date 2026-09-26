package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AimAnimation;
import net.tkg.RenaissanceLib.attachment.AimAnimationModifier;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentStatesModifier;
import net.tkg.RenaissanceLib.attachment.AttachmentToggleTargets;
import net.tkg.RenaissanceLib.attachment.FireAnimation;
import net.tkg.RenaissanceLib.attachment.FireAnimationModifier;
import net.tkg.RenaissanceLib.attachment.ToggleTarget;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class AttachmentAnimationManager {

    private static final int TRACK = 0;
    /** ADS animations get their own track, so they layer with toggle states and fire clips (track 0). */
    private static final int AIM_TRACK = 1;

    private static final float TRANSITION_SECONDS = 0.15f;

    /** Shorter blend for a per-shot fire clip, so rapid fire retriggers it crisply rather than mushily. */
    private static final float FIRE_TRANSITION_SECONDS = 0.05f;

    private static final Map<ResourceLocation, AnimationController> CONTROLLERS = new HashMap<>();
    private static final Map<ResourceLocation, String> LAST_STATE = new HashMap<>();

    private static final Map<ResourceLocation, Boolean> UNAVAILABLE = new HashMap<>();
    /** Last seen ADS state per attachment (aim-in/out style), to catch the press/release edge. */
    private static final Map<ResourceLocation, Boolean> LAST_AIM = new HashMap<>();
    /** Attachments winding {@code aim_in} back (released ADS without an {@code aim_out}). */
    private static final Set<ResourceLocation> REWINDING = new HashSet<>();
    private static final Set<String> WARNED_AIM_CLIPS = new HashSet<>();

    private AttachmentAnimationManager() {}

    public static void tick(ItemStack gunItem) {
        if (IGun.getIGunOrNull(gunItem) == null) return;
        Aim aim = Aim.now();

        // Top-level slots. Toggle states, fire-reaction and ADS animations all play through the same
        // per-attachment controller; drive state changes and the aim clip here and advance the controller once so
        // a fire clip (started by onGunFire on the same controller) also progresses.
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;

            ResourceLocation attachmentId = attachmentId(gunItem, type);
            if (attachmentId == null) continue;

            AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, type);
            boolean hasStates = states != null && states.getAnimationFile() != null;
            FireAnimationModifier.Spec fire = FireAnimation.get(gunItem, type);
            AimAnimationModifier.Spec aimSpec = AimAnimation.get(gunItem, type);
            if (!hasStates && fire == null && aimSpec == null) continue;

            String animationFile = hasStates ? states.getAnimationFile()
                    : fire != null ? fire.getAnimationFile() : aimSpec.getAnimationFile();
            AnimationController controller = getController(attachmentId, animationFile);
            if (controller == null) continue;

            if (hasStates) {
                reconcileStateToZoom(gunItem, type, states, attachmentId);
                runStateClipOnChange(controller, attachmentId, states, AttachmentStates.getState(gunItem, type));
            }
            if (aimSpec != null) driveAim(controller, attachmentId, aimSpec, aim);
            controller.update();
        }

        // Rail-mounted attachments: their own model animation plays through the same shared BedrockAttachmentModel
        // (per attachment id) that the rail sight renderer draws, so driving the controller here animates them.
        ActiveOptic active = ActiveOptic.resolve(gunItem);
        for (ToggleTarget target : AttachmentToggleTargets.railTargets(gunItem)) {
            ItemStack mounted = AttachmentToggleTargets.getItem(gunItem, target);
            AttachmentStatesModifier.States states = AttachmentStates.getStatesForItem(mounted);
            boolean hasStates = states != null && states.getAnimationFile() != null;
            AimAnimationModifier.Spec aimSpec = AimAnimation.getForItem(mounted);
            if (!hasStates && aimSpec == null) continue;

            ResourceLocation attachmentId = idForItem(mounted);
            if (attachmentId == null) continue;

            AnimationController controller = getController(attachmentId,
                    hasStates ? states.getAnimationFile() : aimSpec.getAnimationFile());
            if (controller == null) continue;

            if (hasStates) {
                // Keep the sight's state in step with the shared zoom-key cycle, so cycling the view with the
                // zoom key plays the matching fold/deploy animation (mirrors reconcileStateToZoom for the scope).
                reconcileRailStateToZoom(gunItem, target, states, active);
                runStateClipOnChange(controller, attachmentId, states, AttachmentStates.getState(gunItem, target));
            }
            if (aimSpec != null) driveAim(controller, attachmentId, aimSpec, aim);
            controller.update();
        }
    }

    /** This frame's aim: whether ADS is held, how far the gun has aimed in (0..1), and the time since last frame. */
    private record Aim(boolean aiming, float progress, long frameNs) {
        private static long lastNs = 0L;

        static Aim now() {
            Minecraft mc = Minecraft.getInstance();
            long now = System.nanoTime();
            long frameNs = lastNs == 0L ? 0L : Math.min(now - lastNs, 100_000_000L);
            lastNs = now;
            if (mc.player == null) return new Aim(false, 0f, frameNs);
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            float progress = operator.getClientAimingProgress(mc.getFrameTime());
            return new Aim(operator.isAim(), Float.isFinite(progress) ? Mth.clamp(progress, 0f, 1f) : 0f, frameNs);
        }
    }

    /**
     * Drive an attachment's {@code aim_animation} on its own track ({@link #AIM_TRACK}, so it layers with toggle
     * states and fire clips on track 0).
     * <ul>
     *   <li>{@code follow}: the clip is held paused at the gun's aim progress × its length, every frame.</li>
     *   <li>{@code aim_in}/{@code aim_out}: played on the ADS press/release edge. Without {@code aim_out}, release
     *       winds {@code aim_in} back from wherever it got to; pressing again mid-rewind carries on forward.</li>
     * </ul>
     */
    private static void driveAim(AnimationController controller, ResourceLocation attachmentId,
                                 AimAnimationModifier.Spec spec, Aim aim) {
        String follow = spec.getFollow();
        if (follow != null) {
            ObjectAnimationRunner runner = runnerFor(controller, follow);
            if (runner == null) {
                if (!hasClip(controller, attachmentId, spec, follow)) return;
                controller.runAnimation(AIM_TRACK, follow, ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0f);
                runner = controller.getAnimation(AIM_TRACK);
                if (runner == null) return;
            }
            runner.pause();
            runner.setProgressNs((long) (aim.progress() * runner.getAnimation().getMaxEndTimeS() * 1e9));
            return;
        }

        String aimIn = spec.getAimIn();
        Boolean was = LAST_AIM.put(attachmentId, aim.aiming());
        if (was != null && was != aim.aiming()) {
            REWINDING.remove(attachmentId);
            ObjectAnimationRunner current = runnerFor(controller, aimIn);
            if (aim.aiming()) {
                if (current != null && current.isPausing()) {
                    current.run(); // interrupted rewind — carry on forward from here
                } else if (hasClip(controller, attachmentId, spec, aimIn)) {
                    controller.runAnimation(AIM_TRACK, aimIn, ObjectAnimation.PlayType.PLAY_ONCE_HOLD, TRANSITION_SECONDS);
                }
            } else if (spec.getAimOut() != null) {
                if (hasClip(controller, attachmentId, spec, spec.getAimOut())) {
                    controller.runAnimation(AIM_TRACK, spec.getAimOut(), ObjectAnimation.PlayType.PLAY_ONCE_HOLD,
                            TRANSITION_SECONDS);
                }
            } else if (current != null) {
                current.pause();
                REWINDING.add(attachmentId);
            }
        }
        if (REWINDING.contains(attachmentId)) {
            ObjectAnimationRunner current = runnerFor(controller, aimIn);
            if (current == null) {
                REWINDING.remove(attachmentId);
                return;
            }
            long progress = Math.max(0L, Math.min(current.getProgressNs(),
                    (long) (current.getAnimation().getMaxEndTimeS() * 1e9)) - aim.frameNs());
            current.setProgressNs(progress);
            if (progress == 0L) REWINDING.remove(attachmentId);
        }
    }

    /** The runner on the aim track if it's playing {@code clip}, else {@code null}. */
    @Nullable
    private static ObjectAnimationRunner runnerFor(AnimationController controller, @Nullable String clip) {
        ObjectAnimationRunner runner = controller.getAnimation(AIM_TRACK);
        return runner != null && clip != null && clip.equals(runner.getAnimation().name) ? runner : null;
    }

    private static boolean hasClip(AnimationController controller, ResourceLocation attachmentId,
                                   AimAnimationModifier.Spec spec, String clip) {
        if (controller.containPrototype(clip)) return true;
        if (WARNED_AIM_CLIPS.add(attachmentId + "#" + clip)) {
            RenaissanceLibMod.LOGGER.warn("[RenaissanceLib] Attachment {} has no aim_animation clip '{}' in {}",
                    attachmentId, clip, spec.getAnimationFile());
        }
        return false;
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
        LAST_AIM.clear();
        REWINDING.clear();
        WARNED_AIM_CLIPS.clear();
    }
}
