package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.client.resource.pojo.display.gun.ShellEjection;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAttachments;
import net.tkg.RenaissanceLib.attachment.UnderbarrelDataModifier;

/**
 * Hangs the underbarrel's fire-effect renderers on its model once it has loaded (called from the client
 * attachment-index model-load hook, alongside the rail registrar): the muzzle flash on the
 * {@code muzzle_flash_underbarrel} bone and the shell ejection on the {@code shell} bone. Only underbarrel
 * attachments (those carrying an {@code underbarrel_display}) are wired, and each effect is only registered
 * if the display defines it and the model has the bone.
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelRenderRegistrar {
    private static final String MUZZLE_FLASH_NODE = "muzzle_flash_underbarrel";
    private static final String SHELL_NODE = "shell";
    /** The underbarrel's support-hand locator (same node name TaC:Z uses on the host gun). */
    private static final String LEFT_HAND_NODE = "lefthand_pos";

    private UnderbarrelRenderRegistrar() {}

    public static void register(AttachmentData data, AttachmentDisplay display, BedrockAttachmentModel model) {
        if (model == null || !(display instanceof IUnderbarrelDisplay holder)) return;
        GunDisplay ubDisplay = holder.renaissance$getUnderbarrelDisplay();
        if (ubDisplay == null) return;

        MuzzleFlash muzzleFlash = ubDisplay.getMuzzleFlash();
        if (muzzleFlash != null && model.getNode(MUZZLE_FLASH_NODE) != null) {
            model.setFunctionalRenderer(MUZZLE_FLASH_NODE, part -> new UnderbarrelMuzzleFlashRender(muzzleFlash));
        }

        ShellEjection shell = ubDisplay.getShellEjection();
        ResourceLocation ammoId = ammoIdOf(data);
        if (shell != null && ammoId != null && model.getNode(SHELL_NODE) != null) {
            model.setFunctionalRenderer(SHELL_NODE, part -> new UnderbarrelShellRender(shell, ammoId));
        }

        // Draw the vanilla player left (support) arm at the underbarrel's lefthand_pos while it's the active
        // weapon (the modeled arm geometry there is just a positioning placeholder, which this replaces — the
        // TaC:Z/GeckoLib convention). The host's left hand is hidden in that case (LeftHandRenderMixin) and the
        // arm chain kept visible in the attachment pass (BedrockAttachmentModelMixin).
        if (model.getNode(LEFT_HAND_NODE) != null) {
            model.setFunctionalRenderer(LEFT_HAND_NODE, part -> new UnderbarrelLeftHandRender(model));
        }

        // Draw the underbarrel's OWN attachments (a muzzle, extended mag, …) on its model — the same
        // <type>_pos bone convention TaC:Z uses on a gun. Only for slots the underbarrel actually allows and
        // whose bone exists in the model; the renderer draws nothing when the slot is empty.
        GunData ubData = ubDataOf(data);
        if (ubData != null) {
            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE) continue;
                if (!UnderbarrelAttachments.isAllowed(ubData, type)) continue;
                String posNode = type.name().toLowerCase() + "_pos";
                if (model.getNode(posNode) == null) continue;
                model.setFunctionalRenderer(posNode, part -> {
                    part.visible = false; // the locator bone itself isn't drawn, only the mounted attachment
                    return new UnderbarrelAttachmentRenderer(model, type);
                });
            }
        }
    }

    /** The underbarrel sub-gun's {@link GunData} (from the parsed {@code underbarrel_data}), or {@code null}. */
    private static GunData ubDataOf(AttachmentData data) {
        if (data == null) return null;
        JsonProperty<?> property = data.getModifier().get(UnderbarrelDataModifier.ID);
        return (property != null && property.getValue() instanceof GunData gunData) ? gunData : null;
    }

    /** The underbarrel sub-gun's ammo id (from the parsed {@code underbarrel_data}), or {@code null}. */
    private static ResourceLocation ammoIdOf(AttachmentData data) {
        GunData ubData = ubDataOf(data);
        return ubData != null ? ubData.getAmmoId() : null;
    }
}
