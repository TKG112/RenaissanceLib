package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.AnimationKeyframes;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimation;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads TaC:Z bedrock animation files and samples their keyframes — the raw data layer of our underbarrel
 * animation player. (TaC:Z runs animations through an external library + Lua state machine we can't reuse for
 * an attachment, so we read the keyframes directly and interpolate them ourselves.)
 *
 * <p>{@link #sample} returns the raw interpolated value (position in Blockbench pixels, rotation in degrees,
 * scale as a multiplier); the applier converts those to bone transforms.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelAnimations {
    private UnderbarrelAnimations() {}

    /** The named animation ({@code idle}/{@code shoot}/{@code reload}) from an animation file, or {@code null}. */
    @Nullable
    public static BedrockAnimation get(@Nullable ResourceLocation animationFile, String name) {
        if (animationFile == null || name == null) return null;
        BedrockAnimationFile file = ClientAssetsManager.INSTANCE.getBedrockAnimations(animationFile);
        if (file == null || file.getAnimations() == null) return null;
        return file.getAnimations().get(name);
    }

    /**
     * Interpolate one channel (position/rotation/scale) at {@code time} seconds. Honours per-keyframe lerp mode
     * ({@code catmullrom} → Catmull-Rom spline, else linear) and pre/post values for stepped keyframes.
     * Returns {@code null} if there are no keyframes (leave the bone at rest).
     */
    @Nullable
    public static Vector3f sample(@Nullable AnimationKeyframes channel, double time) {
        if (channel == null || channel.getKeyframes() == null || channel.getKeyframes().isEmpty()) return null;

        List<Double> times = new ArrayList<>();
        List<AnimationKeyframes.Keyframe> frames = new ArrayList<>();
        for (Double2ObjectMap.Entry<AnimationKeyframes.Keyframe> e : channel.getKeyframes().double2ObjectEntrySet()) {
            times.add(e.getDoubleKey());
            frames.add(e.getValue());
        }
        int n = times.size();
        if (time <= times.get(0)) return new Vector3f(pointValue(frames.get(0)));
        if (time >= times.get(n - 1)) return new Vector3f(pointValue(frames.get(n - 1)));

        int i = 0;
        while (i < n - 1 && times.get(i + 1) <= time) i++;
        double ta = times.get(i);
        double tb = times.get(i + 1);
        AnimationKeyframes.Keyframe a = frames.get(i);
        AnimationKeyframes.Keyframe b = frames.get(i + 1);
        float f = (float) ((time - ta) / (tb - ta));

        if ("catmullrom".equals(a.lerpMode())) {
            Vector3f p0 = pointValue(i > 0 ? frames.get(i - 1) : a);
            Vector3f p3 = pointValue(i + 2 < n ? frames.get(i + 2) : b);
            return catmullRom(p0, pointValue(a), pointValue(b), p3, f);
        }
        // Linear: leave keyframe a via its "post" value, arrive keyframe b at its "pre" value.
        return new Vector3f(leaveValue(a)).lerp(arriveValue(b), f);
    }

    /**
     * Keyframes may fill only some of {pre, post, data} (e.g. a {@code {"post": [...]}} keyframe leaves
     * {@code data}/{@code pre} null). These pick a non-null value for each use, defaulting to zero.
     */
    private static Vector3f pointValue(AnimationKeyframes.Keyframe k) {
        return firstNonNull(k.data(), k.post(), k.pre());
    }

    private static Vector3f leaveValue(AnimationKeyframes.Keyframe k) {
        return firstNonNull(k.post(), k.data(), k.pre());
    }

    private static Vector3f arriveValue(AnimationKeyframes.Keyframe k) {
        return firstNonNull(k.pre(), k.data(), k.post());
    }

    private static Vector3f firstNonNull(Vector3f... values) {
        for (Vector3f v : values) {
            if (v != null) return v;
        }
        return new Vector3f();
    }

    private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        return new Vector3f(
                catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), t),
                catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), t),
                catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), t));
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2f * p1)
                + (-p0 + p2) * t
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }
}
