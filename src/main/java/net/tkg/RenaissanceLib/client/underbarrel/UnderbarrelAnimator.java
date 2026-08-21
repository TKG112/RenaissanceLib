package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationBone;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimation;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.tacz.guns.util.math.MathUtil;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Drives the underbarrel's own animations on its attachment model — the piece TaC:Z doesn't provide for
 * attachments. State is a simple current-clip + start-time: {@code idle} loops by default; a one-shot
 * ({@code shoot}/{@code reload}) triggered by the fire/reload paths plays once and falls back to idle.
 *
 * <p>Shell-by-shell (tube shotgun) reloads are supported too: for a {@link FeedType#MANUAL} underbarrel whose
 * model has the {@code reload_intro}/{@code reload_loop}/{@code reload_end} clips, {@link #triggerReload} builds
 * a sequence that plays the intro once, repeats the shell-insert loop to fill the reload duration, then plays
 * the end — mirroring how TaC:Z's Lua state machine reloads shotguns. Any underbarrel without those clips falls
 * back to a single {@code reload} clip.
 *
 * <p>State is global (client, shooter-only) like the other underbarrel effects; multiplayer would need a
 * synced, per-entity state.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelAnimator {
    private static final String IDLE = "idle";
    private static final String RELOAD = "reload";
    private static final String RELOAD_INTRO = "reload_intro";
    private static final String RELOAD_INTRO_EMPTY = "reload_intro_empty";
    private static final String RELOAD_LOOP = "reload_loop";
    private static final String RELOAD_END = "reload_end";
    /** The underbarrel's whole-gun root node, whose animation is layered onto the whole weapon (not the UB model). */
    private static final String ROOT_NODE = "root";

    private static String currentAnim = IDLE;
    private static long startMs = 0L;

    // Shell-by-shell reload sequence state (active only while a manual-feed reload plays).
    private static boolean shellReload = false;
    private static String shellIntroClip = RELOAD_INTRO;
    private static double shellIntroLen;
    private static double shellLoopLen;
    private static double shellEndLen;
    private static double shellTotal;

    private UnderbarrelAnimator() {}

    /** Trigger a one-shot clip ({@code shoot} / single-clip {@code reload}). */
    public static void trigger(String name) {
        currentAnim = name;
        startMs = System.currentTimeMillis();
        shellReload = false;
    }

    /**
     * Trigger the reload animation: a shell-by-shell sequence for a manual-feed underbarrel that has the
     * shotgun reload clips, otherwise a single {@code reload} clip. {@code gun}/{@code underbarrel} identify the
     * installed weapon; {@code ubData} is its sub-gun data.
     */
    public static void triggerReload(ItemStack gun, ItemStack underbarrel, GunData ubData) {
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(underbarrel);
        ResourceLocation animFile = display == null ? null : display.getAnimationLocation();
        boolean manual = ubData != null && ubData.getReloadData() != null
                && ubData.getReloadData().getType() == FeedType.MANUAL;

        if (animFile != null && manual) {
            int current = gun != null ? UnderbarrelAmmo.get(gun, ubData) : 0;
            boolean empty = current <= 0;
            double durationSec = Math.max(0, UnderbarrelAmmo.reloadDurationTicks(gun, ubData, current)) / 20.0;
            if (beginShellReload(animFile, empty, durationSec)) {
                return;
            }
        }
        trigger(RELOAD);
    }

    /**
     * Sets up the shell-by-shell sequence if the model has the required clips. Returns false (caller falls back
     * to a single clip) if any clip is missing.
     */
    private static boolean beginShellReload(ResourceLocation animFile, boolean empty, double durationSec) {
        String introName = empty ? RELOAD_INTRO_EMPTY : RELOAD_INTRO;
        BedrockAnimation intro = UnderbarrelAnimations.get(animFile, introName);
        if (intro == null) {
            introName = RELOAD_INTRO;
            intro = UnderbarrelAnimations.get(animFile, introName);
        }
        BedrockAnimation loop = UnderbarrelAnimations.get(animFile, RELOAD_LOOP);
        BedrockAnimation end = UnderbarrelAnimations.get(animFile, RELOAD_END);
        if (intro == null || loop == null || end == null) {
            return false;
        }
        shellReload = true;
        shellIntroClip = introName;
        shellIntroLen = intro.getAnimationLength();
        shellLoopLen = Math.max(0.01, loop.getAnimationLength());
        shellEndLen = end.getAnimationLength();
        shellTotal = Math.max(durationSec, shellIntroLen + shellEndLen);
        startMs = System.currentTimeMillis();
        currentAnim = RELOAD;
        return true;
    }

    /** Pose the underbarrel model for the current animation frame. Call once per render, before drawing. */
    public static void apply(BedrockAttachmentModel model, ItemStack underbarrel) {
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(underbarrel);
        if (display == null) return;
        ResourceLocation animationFile = display.getAnimationLocation();
        if (animationFile == null) return;

        long now = System.currentTimeMillis();
        double elapsed = (now - startMs) / 1000.0;

        if (shellReload) {
            applyShellReload(model, animationFile, now, elapsed);
            return;
        }

        BedrockAnimation anim = UnderbarrelAnimations.get(animationFile, currentAnim);
        // A finished one-shot falls back to the looping idle.
        if (anim != null && !anim.isLoop() && elapsed >= anim.getAnimationLength()) {
            currentAnim = IDLE;
            startMs = now;
            elapsed = 0.0;
            anim = UnderbarrelAnimations.get(animationFile, IDLE);
        }
        if (anim == null && !IDLE.equals(currentAnim)) {
            anim = UnderbarrelAnimations.get(animationFile, IDLE);
        }
        if (anim == null) return;

        double length = anim.getAnimationLength();
        double time = (anim.isLoop() && length > 0) ? elapsed % length : Math.min(elapsed, length);
        poseModel(model, anim, time);
    }

    /** Plays intro → shell loop (repeated to fill) → end, then hands back to idle. */
    private static void applyShellReload(BedrockAttachmentModel model, ResourceLocation animFile,
                                         long now, double elapsed) {
        if (elapsed >= shellTotal) {
            shellReload = false;
            currentAnim = IDLE;
            startMs = now;
            BedrockAnimation idle = UnderbarrelAnimations.get(animFile, IDLE);
            if (idle != null) {
                poseModel(model, idle, 0.0);
            }
            return;
        }

        double loopStart = shellIntroLen;
        double loopEnd = Math.max(loopStart, shellTotal - shellEndLen);
        String clip;
        double time;
        if (elapsed < loopStart) {
            clip = shellIntroClip;
            time = elapsed;
        } else if (elapsed < loopEnd) {
            clip = RELOAD_LOOP;
            time = (elapsed - loopStart) % shellLoopLen;
        } else {
            clip = RELOAD_END;
            time = elapsed - loopEnd;
        }

        BedrockAnimation anim = UnderbarrelAnimations.get(animFile, clip);
        if (anim == null) return;
        poseModel(model, anim, Math.min(time, anim.getAnimationLength()));
    }

    /** Reset the model to rest, write one clip's sampled pose onto it, and publish the whole-weapon (root) delta. */
    private static void poseModel(BedrockAttachmentModel model, BedrockAnimation anim, double time) {
        resetToRest(model);
        Vector3f rootPos = null;
        Vector3f rootRot = null;
        Map<String, AnimationBone> bones = anim.getBones();
        if (bones != null) {
            for (Map.Entry<String, AnimationBone> entry : bones.entrySet()) {
                AnimationBone channel = entry.getValue();

                // The root (whole-gun) bone's animation drives the WHOLE weapon through the anchor, not the
                // underbarrel model — applying it to the model would swing the underbarrel off its mount ("off
                // the rail"). Capture it for the anchor and skip the model.
                if (ROOT_NODE.equals(entry.getKey())) {
                    rootPos = UnderbarrelAnimations.sample(channel.getPosition(), time);
                    rootRot = UnderbarrelAnimations.sample(channel.getRotation(), time);
                    continue;
                }

                BedrockPart bone = model.getNode(entry.getKey());
                if (bone == null) continue;

                Vector3f pos = UnderbarrelAnimations.sample(channel.getPosition(), time);
                if (pos != null) {
                    // Animation position is authored in Blockbench pixels; the bone offset is in blocks.
                    // Y is negated to match TaC:Z's ModelTranslateListener (bedrock part space inverts Y).
                    bone.offsetX = pos.x() / 16f;
                    bone.offsetY = -pos.y() / 16f;
                    bone.offsetZ = pos.z() / 16f;
                }
                Vector3f rot = UnderbarrelAnimations.sample(channel.getRotation(), time);
                if (rot != null) {
                    // Rest rotation stays on the Euler fields; the animation rotation is a separate quaternion
                    // applied after it (matches TaC:Z's additionalQuaternion), correct even with a non-zero rest.
                    MathUtil.toQuaternion(
                            (float) Math.toRadians(rot.x()),
                            (float) Math.toRadians(rot.y()),
                            (float) Math.toRadians(rot.z()),
                            bone.additionalQuaternion);
                }
                Vector3f scale = UnderbarrelAnimations.sample(channel.getScale(), time);
                if (scale != null) {
                    bone.xScale = scale.x();
                    bone.yScale = scale.y();
                    bone.zScale = scale.z();
                }
            }
        }
        publishWeaponDelta(rootPos, rootRot);
    }

    /**
     * Publish the underbarrel {@code root} bone's sampled animation so it moves the WHOLE weapon (host +
     * underbarrel) through {@link UnderbarrelCameraAnchor} — the underbarrel's recoil/reload swing then carries
     * the host gun with it and stays on the rail, instead of rotating the underbarrel model off its mount.
     * {@code null}/zero (idle) → cleared, so the host is untouched at rest.
     */
    private static void publishWeaponDelta(Vector3f pos, Vector3f rot) {
        if (pos == null && rot == null) {
            UnderbarrelCameraAnchor.clear();
            return;
        }
        // Same pixel→block / inverted-Y convention as the per-bone application above.
        float ox = pos != null ? pos.x() / 16f : 0f;
        float oy = pos != null ? -pos.y() / 16f : 0f;
        float oz = pos != null ? pos.z() / 16f : 0f;
        Quaternionf q = new Quaternionf();
        if (rot != null) {
            MathUtil.toQuaternion(
                    (float) Math.toRadians(rot.x()),
                    (float) Math.toRadians(rot.y()),
                    (float) Math.toRadians(rot.z()),
                    q);
        }
        UnderbarrelCameraAnchor.setCameraAnimation(ox, oy, oz, q);
    }

    /** Reset every bone to its rest pose so the previous frame's animation doesn't linger. */
    private static void resetToRest(BedrockAttachmentModel model) {
        List<BedrockPart> roots = ((BedrockModel) (Object) model).getShouldRender();
        if (roots == null) return;
        for (BedrockPart root : roots) {
            resetBone(root);
        }
    }

    private static void resetBone(BedrockPart part) {
        if (part == null) return;
        part.offsetX = 0f;
        part.offsetY = 0f;
        part.offsetZ = 0f;
        part.xRot = part.getInitRotX();
        part.yRot = part.getInitRotY();
        part.zRot = part.getInitRotZ();
        // Clear last frame's animation rotation; a bone with no rotation channel this frame stays at rest.
        part.additionalQuaternion.set(0f, 0f, 0f, 1f);
        part.xScale = 1f;
        part.yScale = 1f;
        part.zScale = 1f;
        if (part.children != null) {
            for (BedrockPart child : part.children) {
                resetBone(child);
            }
        }
    }
}
