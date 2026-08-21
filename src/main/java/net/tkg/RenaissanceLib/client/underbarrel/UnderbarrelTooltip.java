package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.datafixers.util.Either;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.Underbarrel;

/**
 * Adds the underbarrel sub-gun's stat block to the underbarrel attachment item's tooltip as a graphical
 * component ({@link UnderbarrelStatsTooltip} → {@code ClientUnderbarrelStatsTooltip}) — the ammo icon, damage,
 * fire rate and fire modes, styled like a gun's description. TaC:Z already renders the attachment's own
 * modifiers (ADS speed, etc.) in its attachment tooltip, so both show together.
 *
 * <p>A no-op for any item that isn't an underbarrel grip (the data lookup returns null cheaply otherwise).
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class UnderbarrelTooltip {
    private UnderbarrelTooltip() {}

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        GunData data = Underbarrel.getUnderbarrelData(stack);
        if (data == null) return;
        // Insert right after the item name (index 0) so our stat block comes before TaC:Z's attachment tooltip.
        int index = Math.min(1, event.getTooltipElements().size());
        event.getTooltipElements().add(index, Either.right(new UnderbarrelStatsTooltip(data)));
    }
}
