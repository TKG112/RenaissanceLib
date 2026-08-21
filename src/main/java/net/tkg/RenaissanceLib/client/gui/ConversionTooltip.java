package net.tkg.RenaissanceLib.client.gui;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ConversionKit;

import java.util.List;

/**
 * Adds a "Converts to: &lt;gun&gt;" line (and a note that it locks other attachments) to a conversion-kit
 * item's tooltip, so an author or player can see at a glance what weapon the kit yields. The converted
 * gun's display name is read from its client index, exactly as TaC:Z names guns.
 *
 * <p>A no-op for any item that isn't a conversion kit (the id lookup returns null cheaply otherwise).
 */
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public final class ConversionTooltip {
    private ConversionTooltip() {}

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation converted = ConversionKit.getKitConvertedGunId(stack);
        if (converted == null) return;

        Component gunName = TimelessAPI.getClientGunIndex(converted)
                .map(index -> (Component) Component.translatable(index.getName()))
                .orElse(Component.literal(converted.toString()));

        MutableComponent convertsTo = Component.translatable("tooltip.renaissance_lib.conversion.converts_to")
                .withStyle(ChatFormatting.GOLD)
                .append(gunName.copy().withStyle(ChatFormatting.WHITE));
        MutableComponent locks = Component.translatable("tooltip.renaissance_lib.conversion.locks")
                .withStyle(ChatFormatting.DARK_GRAY);

        List<Component> tip = event.getToolTip();
        int index = Math.min(1, tip.size()); // right after the item name
        tip.add(index, convertsTo);
        tip.add(index + 1, locks);
    }
}
