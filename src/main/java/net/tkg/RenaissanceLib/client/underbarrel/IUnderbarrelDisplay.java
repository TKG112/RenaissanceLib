package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;

import javax.annotation.Nullable;

/**
 * Client-side accessor woven onto TaC:Z's {@code AttachmentDisplay} so an underbarrel attachment can
 * carry the two display-file extras TaC:Z itself ignores: the embedded {@code underbarrel_display}
 * (a full {@link GunDisplay} for the sub-gun's model/animation/muzzle-flash/shell/sounds) and the
 * {@code hide_tactical_handguard} flag (suppresses the host gun's tactical handguard so the
 * underbarrel's own handguard geometry shows through — see the handguard render hook).
 *
 * <p>Populated from the raw display JSON at parse time; read back through {@link
 * net.tkg.RenaissanceLib.attachment.Underbarrel}.
 */
public interface IUnderbarrelDisplay {
    boolean renaissance$isHideTacticalHandguard();

    void renaissance$setHideTacticalHandguard(boolean hide);

    @Nullable
    GunDisplay renaissance$getUnderbarrelDisplay();

    void renaissance$setUnderbarrelDisplay(@Nullable GunDisplay display);

    /**
     * Manual mount nudge for placing the underbarrel model on a host gun that has no dedicated underbarrel
     * mount node: {@code [posX, posY, posZ, rotX, rotY, rotZ]} — position in Blockbench pixels, rotation in
     * degrees — or {@code null} if none was authored. Applied on top of the grip-slot mount.
     */
    @Nullable
    float[] renaissance$getMountOffset();

    void renaissance$setMountOffset(@Nullable float[] offset);
}
