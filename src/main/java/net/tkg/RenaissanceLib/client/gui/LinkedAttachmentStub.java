package net.tkg.RenaissanceLib.client.gui;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A stand-in {@link IAttachment} reported for a linked gun inside TaC:Z's refit inventory scan, so its grip
 * candidate button is created through TaC:Z's own layout/paging. Only {@link #getType} matters there (it must
 * read as {@link AttachmentType#GRIP}); the rest are inert. See {@code GunRefitScreenItemLinkMixin}.
 */
public final class LinkedAttachmentStub implements IAttachment {

    public static final LinkedAttachmentStub INSTANCE = new LinkedAttachmentStub();

    private LinkedAttachmentStub() {}

    @Override
    public AttachmentType getType(ItemStack stack) {
        return AttachmentType.GRIP;
    }

    @Override public ResourceLocation getAttachmentId(ItemStack stack) { return null; }
    @Override public void setAttachmentId(ItemStack stack, ResourceLocation id) {}
    @Override public ResourceLocation getSkinId(ItemStack stack) { return null; }
    @Override public void setSkinId(ItemStack stack, ResourceLocation id) {}
    @Override public int getZoomNumber(ItemStack stack) { return 0; }
    @Override public void setZoomNumber(ItemStack stack, int zoom) {}
    @Override public boolean hasCustomLaserColor(ItemStack stack) { return false; }
    @Override public int getLaserColor(ItemStack stack) { return 0; }
    @Override public void setLaserColor(ItemStack stack, int color) {}
}
