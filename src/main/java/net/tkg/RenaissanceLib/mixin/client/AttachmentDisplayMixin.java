package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.client.underbarrel.IUnderbarrelDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

/**
 * Carries the underbarrel display-file extras on TaC:Z's {@link AttachmentDisplay} — the embedded
 * sub-gun {@link GunDisplay} ({@code underbarrel_display}) and the {@code hide_tactical_handguard}
 * flag. TaC:Z's own POJO drops these unknown fields, so they're parsed and stashed here by
 * {@link AttachmentDisplayParseMixin}.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = AttachmentDisplay.class, remap = false)
public abstract class AttachmentDisplayMixin implements IUnderbarrelDisplay {

    @Unique
    private boolean renaissance$hideTacticalHandguard;

    @Unique
    @Nullable
    private GunDisplay renaissance$underbarrelDisplay;

    @Unique
    @Nullable
    private float[] renaissance$mountOffset;

    @Override
    public boolean renaissance$isHideTacticalHandguard() {
        return renaissance$hideTacticalHandguard;
    }

    @Override
    public void renaissance$setHideTacticalHandguard(boolean hide) {
        this.renaissance$hideTacticalHandguard = hide;
    }

    @Override
    @Nullable
    public GunDisplay renaissance$getUnderbarrelDisplay() {
        return renaissance$underbarrelDisplay;
    }

    @Override
    public void renaissance$setUnderbarrelDisplay(@Nullable GunDisplay display) {
        this.renaissance$underbarrelDisplay = display;
    }

    @Override
    @Nullable
    public float[] renaissance$getMountOffset() {
        return renaissance$mountOffset;
    }

    @Override
    public void renaissance$setMountOffset(@Nullable float[] offset) {
        this.renaissance$mountOffset = offset;
    }
}
