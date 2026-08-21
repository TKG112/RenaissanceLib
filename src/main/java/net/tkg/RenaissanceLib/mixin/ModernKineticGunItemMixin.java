package net.tkg.RenaissanceLib.mixin;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentOverrides;
import net.tkg.RenaissanceLib.attachment.BinaryFireMode;
import net.tkg.RenaissanceLib.attachment.ConversionKit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(value = ModernKineticGunItem.class, remap = false)
public abstract class ModernKineticGunItemMixin {

    /**
     * Conversion kits (approach B). {@code getGunId} is the identity every
     * {@code getCommonGunIndex(...)} lookup funnels through, so a valid installed kit makes the whole
     * weapon resolve as its target gun — data, model, display, ammo, fire control and
     * {@code allow_attachments} all swap here in one place, and the attachment locking falls out of the
     * converted gun's own allow list.
     *
     * <p>Mixin 0.8.5 can't inject into the interface {@code default} that supplies {@code getGunId}, so
     * this merges a concrete override onto {@code ModernKineticGunItem} (which only inherited the default)
     * — the class method wins over the interface default. When no kit is installed it reproduces TaC:Z's
     * vanilla NBT read verbatim. {@link ConversionKit#getConversionTarget(ItemStack)} reads only raw gun
     * NBT and the kit attachment (never {@code getGunId}), so this cannot recurse.
     */
    public ResourceLocation getGunId(ItemStack gun) {
        ResourceLocation target = ConversionKit.getConversionTarget(gun);
        if (target != null) return target;
        CompoundTag nbt = gun.getOrCreateTag();
        if (nbt.contains(GunItemDataAccessor.GUN_ID_TAG, Tag.TAG_STRING)) {
            ResourceLocation gunId = ResourceLocation.tryParse(nbt.getString(GunItemDataAccessor.GUN_ID_TAG));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    @Inject(method = "fireSelect", at = @At("HEAD"), cancellable = true, remap = false)
    private void renaissance$attachmentAwareFireSelect(ShooterDataHolder dataHolder,
                                                       ItemStack gunItem,
                                                       CallbackInfo ci) {
        try {
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) return;

            var indexOpt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem));
            if (indexOpt.isEmpty()) return;
            GunData gunData = indexOpt.get().getGunData();

            List<FireMode> available = AttachmentOverrides.effectiveFireModes(gunItem, gunData);
            boolean binaryCapable = AttachmentOverrides.isBinaryCapable(gunItem, gunData);

            if (!binaryCapable) {
                if (available.isEmpty()) return;
                if (available.equals(gunData.getFireModeSet())) return;
                FireMode current = iGun.getFireMode(gunItem);
                int nextIndex = (available.indexOf(current) + 1) % available.size();
                iGun.setFireMode(gunItem, available.get(nextIndex));
                ci.cancel();
                return;
            }

            // Binary-capable: weave the binary pseudo-mode into the cycle. If the gun authored it in
            // its fire_mode array we honour that position (recorded as a 1-based index); otherwise it
            // sits right after SEMI (or at the end). Landing on it sets the underlying mode to SEMI
            // and flags binary; any real mode clears the flag.
            if (available.isEmpty()) available = gunData.getFireModeSet();
            List<Object> entries = new ArrayList<>(available);
            int insertAt = binaryInsertIndex(gunData);
            if (insertAt >= 0) {
                entries.add(Math.min(insertAt, entries.size()), BinaryFireMode.MARKER);
            } else {
                int semiIndex = entries.indexOf(FireMode.SEMI);
                if (semiIndex >= 0) {
                    entries.add(semiIndex + 1, BinaryFireMode.MARKER);
                } else {
                    entries.add(BinaryFireMode.MARKER);
                }
            }

            int currentIndex = BinaryFireMode.isActive(gunItem)
                    ? entries.indexOf(BinaryFireMode.MARKER)
                    : entries.indexOf(iGun.getFireMode(gunItem));
            if (currentIndex < 0) currentIndex = 0;

            Object next = entries.get((currentIndex + 1) % entries.size());
            if (next == BinaryFireMode.MARKER) {
                iGun.setFireMode(gunItem, FireMode.SEMI);
                BinaryFireMode.setActive(gunItem, true);
            } else {
                iGun.setFireMode(gunItem, (FireMode) next);
                BinaryFireMode.setActive(gunItem, false);
            }
            ci.cancel();
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] attachment-aware fireSelect failed", t);
        }
    }

    /** The 0-based cycle position for binary authored in the gun's {@code fire_mode} array, or -1. */
    private static int binaryInsertIndex(GunData gunData) {
        Map<String, Object> params = gunData.getScriptParam();
        if (params != null && params.get("binary_fire_mode") instanceof Number number && number.doubleValue() >= 1) {
            return (int) number.doubleValue() - 1;
        }
        return -1;
    }
}
