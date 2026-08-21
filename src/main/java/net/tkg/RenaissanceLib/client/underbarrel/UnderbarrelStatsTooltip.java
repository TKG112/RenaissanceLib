package net.tkg.RenaissanceLib.client.underbarrel;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Tooltip data for an underbarrel attachment's sub-gun stats — carries the sub-gun's {@link GunData} to the
 * client renderer ({@code ClientUnderbarrelStatsTooltip}). Added to the attachment tooltip by
 * {@link UnderbarrelTooltip} so the underbarrel shows a gun-style stat block (ammo icon, damage, fire rate…)
 * alongside TaC:Z's own attachment-modifier tooltip.
 */
public class UnderbarrelStatsTooltip implements TooltipComponent {
    private final GunData data;

    public UnderbarrelStatsTooltip(GunData data) {
        this.data = data;
    }

    public GunData getData() {
        return data;
    }
}
