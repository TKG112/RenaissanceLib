package net.tkg.RenaissanceLib.client;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The rail-bearing attachment currently being rendered as a <em>standalone item</em> (held in hand, in
 * an item frame, dropped, …) rather than installed on a gun.
 *
 * <p>When TaC:Z renders an attachment item it calls {@code BedrockAttachmentModel.render(null, null, …)}
 * — no gun — so {@link RailSightRenderer} has no gun to read mounted optics from and would draw nothing.
 * The {@code AttachmentItemRenderer} mixin sets this to the attachment stack around that render so the
 * rail renderer can instead read the mounted optics from the item's own NBT and draw them on the model.
 *
 * <p>Client render thread only; a plain static suffices.
 */
@OnlyIn(Dist.CLIENT)
public final class RailStandaloneContext {
    private RailStandaloneContext() {}

    private static ItemStack current = ItemStack.EMPTY;

    public static void begin(ItemStack attachment) {
        current = attachment == null ? ItemStack.EMPTY : attachment;
    }

    public static void end() {
        current = ItemStack.EMPTY;
    }

    public static ItemStack current() {
        return current;
    }
}
