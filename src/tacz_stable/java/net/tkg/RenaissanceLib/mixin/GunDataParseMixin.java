package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.resource.manager.JsonDataManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder — <b>STABLE variant</b>. The TaC:Z release parses gun data through {@code JsonDataManager.parseJson},
 * which {@link JsonDataManagerMixin} already hooks for the {@code "binary"} / {@code "manual"} fire-mode
 * translation. The beta's separate {@code GunDataManager} needs its own hook (see {@code src/tacz_beta}); this
 * empty mixin only exists so both builds share one mixin list.
 */
@Mixin(value = JsonDataManager.class, remap = false)
public abstract class GunDataParseMixin {
}
