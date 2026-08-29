package net.tkg.RenaissanceLib.attachment;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code attachmentId -> item_link} map, rebuilt on every gun-pack (re)load. Populated on both sides — the
 * server parses it from the attachment index jsons ({@code CommonDataManagerItemLinkMixin}); the client parses
 * it from the synced raw jsons ({@code CommonNetworkCacheItemLinkMixin}) — so the refit picker (client) and the
 * install validation (server) both see the same links.
 *
 * <p>An underbarrel with an {@code item_link} becomes installable in a gun's grip slot when the player owns the
 * linked gun, even without the underbarrel attachment item itself (see the item-link refit/install code).
 */
public final class ItemLinkRegistry {

    private static final Map<ResourceLocation, ItemLink> LINKS = new ConcurrentHashMap<>();

    private ItemLinkRegistry() {}

    /** Replace all entries with a freshly parsed set (called at the start of each reload before re-adding). */
    public static void clear() {
        LINKS.clear();
    }

    public static void put(ResourceLocation attachmentId, ItemLink link) {
        if (attachmentId != null && link != null) LINKS.put(attachmentId, link);
    }

    public static ItemLink get(ResourceLocation attachmentId) {
        return LINKS.get(attachmentId);
    }

    public static boolean isEmpty() {
        return LINKS.isEmpty();
    }

    /**
     * The id of an underbarrel attachment whose {@code item_link} matches the given (owned) stack, or
     * {@code null} if none — i.e. "does owning this item unlock some underbarrel?"
     */
    public static ResourceLocation findAttachmentForItem(ItemStack owned) {
        if (owned == null || owned.isEmpty() || LINKS.isEmpty()) return null;
        for (Map.Entry<ResourceLocation, ItemLink> e : LINKS.entrySet()) {
            if (e.getValue().matches(owned)) return e.getKey();
        }
        return null;
    }
}
