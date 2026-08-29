package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

/**
 * A parsed {@code item_link} declared on an underbarrel attachment's index — the gun item whose ownership makes
 * the underbarrel installable (see {@link ItemLinkRegistry}).
 *
 * <p>Authored as an item id with optional SNBT, e.g. {@code "tacz:modern_kinetic_gun{GunId:\"example:testgun\"}"}.
 * A TaC:Z gun's real identity is its {@code GunId} NBT (the item is always {@code modern_kinetic_gun}), so when a
 * {@code GunId} is present that is what we match on; otherwise we fall back to the plain item id.
 *
 * @param itemId the item registry id (e.g. {@code tacz:modern_kinetic_gun})
 * @param gunId  the TaC:Z gun id from the {@code GunId} tag, or {@code null} if none was given
 */
public record ItemLink(ResourceLocation itemId, ResourceLocation gunId) {

    /** Parse an {@code item_link} string; returns {@code null} if it can't be understood. */
    public static ItemLink parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String trimmed = raw.trim();
            int brace = trimmed.indexOf('{');
            String idPart = brace >= 0 ? trimmed.substring(0, brace) : trimmed;
            ResourceLocation itemId = new ResourceLocation(idPart.trim());
            ResourceLocation gunId = null;
            if (brace >= 0) {
                CompoundTag tag = TagParser.parseTag(trimmed.substring(brace));
                if (tag.contains("GunId")) {
                    String g = tag.getString("GunId");
                    if (!g.isBlank()) gunId = new ResourceLocation(g);
                }
            }
            return new ItemLink(itemId, gunId);
        } catch (Exception e) {
            RenaissanceLibMod.LOGGER.warn("[RenaissanceLib] bad item_link '{}': {}", raw, e.toString());
            return null;
        }
    }

    /** True if {@code stack} is the linked gun: matched by {@code GunId} when one was declared, else by item id. */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (gunId != null) {
            IGun iGun = IGun.getIGunOrNull(stack);
            return iGun != null && gunId.equals(iGun.getGunId(stack));
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId.equals(key);
    }
}
