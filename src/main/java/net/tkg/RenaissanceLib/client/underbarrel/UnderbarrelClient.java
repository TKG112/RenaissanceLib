package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Client-only companion to {@link net.tkg.RenaissanceLib.attachment.Underbarrel}: reads the underbarrel
 * display extras ({@code underbarrel_display} sub-gun {@link GunDisplay}, {@code hide_tactical_handguard})
 * that {@link net.tkg.RenaissanceLib.mixin.client.AttachmentDisplayParseMixin} stashed on the attachment
 * display. Resolves the display from an attachment item via TaC:Z's client index (exposed through
 * {@link IUnderbarrelAttachmentIndex}).
 */
@OnlyIn(Dist.CLIENT)
public final class UnderbarrelClient {
    private UnderbarrelClient() {}

    /** The underbarrel display extras for an attachment item, or {@code null} if it carries none. */
    @Nullable
    private static IUnderbarrelDisplay displayOf(ItemStack attachmentItem) {
        if (attachmentItem == null || attachmentItem.isEmpty()) return null;
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        AttachmentDisplay display = TimelessAPI.getClientAttachmentIndex(
                        iAttachment.getAttachmentId(attachmentItem))
                .map(index -> index instanceof IUnderbarrelAttachmentIndex accessor
                        ? accessor.renaissance$getDisplay() : null)
                .orElse(null);
        return display instanceof IUnderbarrelDisplay holder ? holder : null;
    }

    /** Whether this underbarrel wants the host gun's tactical handguard hidden. */
    public static boolean isHideTacticalHandguard(ItemStack attachmentItem) {
        IUnderbarrelDisplay holder = displayOf(attachmentItem);
        return holder != null && holder.renaissance$isHideTacticalHandguard();
    }

    /** The embedded sub-gun {@link GunDisplay} for an underbarrel attachment item, or {@code null}. */
    @Nullable
    public static GunDisplay getUnderbarrelDisplay(ItemStack attachmentItem) {
        IUnderbarrelDisplay holder = displayOf(attachmentItem);
        return holder == null ? null : holder.renaissance$getUnderbarrelDisplay();
    }

    /**
     * The underbarrel's own authored fire sound to play: the silenced sound when {@code silenced} (and it's
     * authored), else the shoot sound. {@code thirdPerson} picks the {@code *_3p} variant (falling back to
     * the first-person key). Returns {@code null} if the underbarrel authored no matching sound.
     */
    @Nullable
    public static ResourceLocation getFireSound(ItemStack attachmentItem, boolean silenced, boolean thirdPerson) {
        GunDisplay display = getUnderbarrelDisplay(attachmentItem);
        if (display == null || display.getSounds() == null) return null;
        Map<String, ResourceLocation> sounds = display.getSounds();
        if (silenced) {
            ResourceLocation silence = sounds.get(thirdPerson ? "silence_3p" : "silence");
            if (silence == null && thirdPerson) silence = sounds.get("silence");
            if (silence != null) return silence; // else fall back to the normal shoot sound below
        }
        ResourceLocation shoot = sounds.get(thirdPerson ? "shoot_3p" : "shoot");
        return (shoot == null && thirdPerson) ? sounds.get("shoot") : shoot;
    }

    /**
     * The manual mount nudge {@code [posX,posY,posZ (px), rotX,rotY,rotZ (deg)]} for placing this underbarrel
     * on a host gun without a dedicated mount node, or {@code null} if none was authored.
     */
    @Nullable
    public static float[] getMountOffset(ItemStack attachmentItem) {
        IUnderbarrelDisplay holder = displayOf(attachmentItem);
        return holder == null ? null : holder.renaissance$getMountOffset();
    }
}
