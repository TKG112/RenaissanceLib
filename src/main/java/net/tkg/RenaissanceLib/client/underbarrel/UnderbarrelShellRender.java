package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.pojo.display.gun.ShellEjection;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Ejects and simulates the underbarrel's spent casings, mirroring TaC:Z's {@code ShellRender} (which is
 * hard-bound to a gun model and can't be reused for an attachment). A casing is queued on fire and rendered
 * as a free-flying object in the frame captured at the {@code shell} bone: position follows
 * {@code (initialVelocity + randomOffset)·t + ½·acceleration·t²}, with {@code angularVelocity·t} spin, drawn
 * with the ammo's shell model, for {@code living_time} seconds. Registered on the {@code shell} bone by
 * {@link UnderbarrelRenderRegistrar}; the queue is global (drives the local shooter's underbarrel only, like
 * the muzzle flash).
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelShellRender implements IFunctionalRenderer {

    private static final class Shell {
        final long spawnMs;
        final Vector3f randomOffset;
        Matrix4f pose;
        Matrix3f normal;

        Shell(long spawnMs, Vector3f randomOffset) {
            this.spawnMs = spawnMs;
            this.randomOffset = randomOffset;
        }
    }

    private static final ConcurrentLinkedDeque<Shell> QUEUE = new ConcurrentLinkedDeque<>();

    private final ShellEjection ejection;
    private final ResourceLocation ammoId;

    public UnderbarrelShellRender(ShellEjection ejection, ResourceLocation ammoId) {
        this.ejection = ejection;
        this.ammoId = ammoId;
    }

    /** Queue a casing (a random per-shot spread from {@code random_velocity}). Called on underbarrel fire. */
    public static void eject(ShellEjection ejection) {
        if (ejection == null) return;
        Vector3f rv = ejection.getRandomVelocity();
        Vector3f offset = rv == null ? new Vector3f()
                : new Vector3f((float) (Math.random() * rv.x()),
                (float) (Math.random() * rv.y()),
                (float) (Math.random() * rv.z()));
        QUEUE.add(new Shell(System.currentTimeMillis(), offset));
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, ItemDisplayContext ctx,
                       int light, int overlay) {
        if (QUEUE.isEmpty() || ejection == null || ammoId == null) return;

        ClientAmmoIndex index = TimelessAPI.getClientAmmoIndex(ammoId).orElse(null);
        if (index == null) return;
        BedrockAmmoModel shellModel = index.getShellModel();
        ResourceLocation shellTexture = index.getShellTextureLocation();
        if (shellModel == null || shellTexture == null) return;

        long now = System.currentTimeMillis();
        long lifeMs = (long) (ejection.getLivingTime() * 1000f);

        Iterator<Shell> it = QUEUE.iterator();
        while (it.hasNext()) {
            Shell shell = it.next();
            if (now - shell.spawnMs > lifeMs) {
                it.remove();
                continue;
            }
            // Capture the shell-bone frame the first time this casing is drawn.
            if (shell.pose == null) {
                shell.pose = new Matrix4f(poseStack.last().pose());
                shell.normal = new Matrix3f(poseStack.last().normal());
            }
            renderSingleShell(ctx, light, overlay, shell, shellModel, shellTexture, now);
        }
    }

    private void renderSingleShell(ItemDisplayContext ctx, int light, int overlay, Shell shell,
                                   BedrockAmmoModel model, ResourceLocation texture, long now) {
        float t = (now - shell.spawnMs) / 1000f;
        Vector3f iv = ejection.getInitialVelocity();
        Vector3f a = ejection.getAcceleration();
        Vector3f av = ejection.getAngularVelocity();

        PoseStack ps = new PoseStack();
        ps.last().normal().mul(shell.normal);
        ps.last().pose().mul(shell.pose);

        double x = (iv.x() + shell.randomOffset.x()) * t + 0.5 * a.x() * t * t;
        double y = (iv.y() + shell.randomOffset.y()) * t + 0.5 * a.y() * t * t;
        double z = (iv.z() + shell.randomOffset.z()) * t + 0.5 * a.z() * t * t;
        ps.translate(x, y, z);

        ps.mulPose(Axis.XP.rotationDegrees(av.x() * t));
        ps.mulPose(Axis.YP.rotationDegrees(av.y() * t));
        ps.mulPose(Axis.ZP.rotationDegrees(av.z() * t));

        model.render(ps, ctx, RenderType.entityCutout(texture), light, overlay);
    }
}
