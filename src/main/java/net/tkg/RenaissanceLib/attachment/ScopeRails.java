package net.tkg.RenaissanceLib.attachment;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Rail system helper: reports the rail mount slots contributed by the scope currently installed on
 * a gun. A scope becomes rail-capable by declaring a {@code rails} block (see {@link RailsModifier}).
 *
 * <p>Detection mirrors the fire-mode/binary scan: the rails config is authored on the scope, so we
 * look for any installed attachment carrying a {@code rails} modifier value.
 */
public final class ScopeRails {
    private ScopeRails() {}

    /** The rail slots for the gun's installed <em>scope</em> (the optic rail host), or empty. */
    public static List<RailsModifier.RailSlot> getRailSlots(ItemStack gunItem) {
        RailsModifier.Spec spec = getRailsSpec(gunItem);
        return spec == null ? Collections.emptyList() : spec.getSlots();
    }

    /** The full rails spec for the gun's installed <em>scope</em> (the optic rail host), or {@code null}. */
    public static RailsModifier.Spec getRailsSpec(ItemStack gunItem) {
        return getRailsSpecForType(gunItem, AttachmentType.SCOPE);
    }

    /** The rails spec of the attachment installed in the given native slot on the gun, or {@code null}. */
    public static RailsModifier.Spec getRailsSpecForType(ItemStack gunItem, AttachmentType hostType) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return null;
        ItemStack host = iGun.getAttachment(gunItem, hostType);
        return host.isEmpty() ? null : readRailsSpec(host);
    }

    /**
     * One rail-hosting native slot on the gun: which slot ({@link #type}), the installed host attachment
     * ({@link #item}) and its declared rails ({@link #spec}).
     */
    public record RailHost(AttachmentType type, ItemStack item, RailsModifier.Spec spec) {}

    /**
     * Every native slot whose installed attachment declares a non-empty {@code rails} block, in slot order.
     * A scope may host canted/top optic rails; a grip/handguard may host a laser rail; etc. Server-safe
     * (reads the common attachment data).
     */
    public static List<RailHost> getRailHosts(ItemStack gunItem) {
        List<RailHost> hosts = new java.util.ArrayList<>();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) return hosts;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack item = iGun.getAttachment(gunItem, type);
            if (item.isEmpty()) continue;
            RailsModifier.Spec spec = readRailsSpec(item);
            if (spec != null) hosts.add(new RailHost(type, item, spec));
        }
        return hosts;
    }

    public static boolean isRailCapable(ItemStack gunItem) {
        return !getRailHosts(gunItem).isEmpty();
    }

    /**
     * The rails spec declared by an arbitrary attachment <em>item</em> that is a valid <em>optic</em> host,
     * or {@code null}. Gated to optics so the recursive nesting tree stays optic-only (a scope may carry
     * further optics; a laser/grip mount may not nest more rails). Used to walk the recursive mount tree.
     */
    public static RailsModifier.Spec getRailsSpecForAttachment(ItemStack attachmentItem) {
        return isOptic(attachmentItem) ? readRailsSpec(attachmentItem) : null;
    }

    /** Pure reader: an attachment item's rails spec (any type), or {@code null} if it declares none. */
    private static RailsModifier.Spec readRailsSpec(ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment == null) return null;
        return TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(attachmentItem))
                .map(index -> index.getData().getModifier().get(RailsModifier.ID))
                .filter(property -> property != null
                        && property.getValue() instanceof RailsModifier.Spec spec && !spec.getSlots().isEmpty())
                .map(property -> (RailsModifier.Spec) property.getValue())
                .orElse(null);
    }

    /**
     * Whether an attachment is a rail host at all — i.e. it declares a {@code rails} block (any slot type).
     * Used as the top-level render gate. The stricter "optics only nest on scopes" rule is enforced where
     * mounts are <em>accepted</em> (the refit picker and the server install check), not here.
     */
    public static boolean isRailHost(ItemStack attachment) {
        return readRailsSpec(attachment) != null;
    }

    /** Whether a mounted item is an optic (a {@code SCOPE}-type attachment you aim through). */
    public static boolean isOptic(ItemStack attachment) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        return iAttachment != null && iAttachment.getType(attachment) == AttachmentType.SCOPE;
    }

    /**
     * The TaC:Z attachment type an {@code allow} category maps to, or {@code null} for {@link
     * RailsModifier.RailSlot#ALLOW_ANY} (any type). {@code scope}/{@code sight} both map to {@code SCOPE}
     * (the scope/sight distinction is client-only display data — refined in the UI, not here).
     */
    @Nullable
    public static AttachmentType categoryType(String category) {
        switch (category) {
            case RailsModifier.RailSlot.ALLOW_SCOPE:
            case RailsModifier.RailSlot.ALLOW_SIGHT:
                return AttachmentType.SCOPE;
            case RailsModifier.RailSlot.ALLOW_LASER:
                return AttachmentType.LASER;
            case "grip":
                return AttachmentType.GRIP;
            case "muzzle":
                return AttachmentType.MUZZLE;
            case "stock":
                return AttachmentType.STOCK;
            case "extended_mag":
            case "magazine":
                return AttachmentType.EXTENDED_MAG;
            default:
                return null;
        }
    }

    /**
     * Server-safe (coarse) check on a {@link AttachmentType#SCOPE} host — see
     * {@link #typeAllowed(List, AttachmentType, AttachmentType)}.
     */
    public static boolean typeAllowed(List<String> allow, AttachmentType mountType) {
        return typeAllowed(allow, mountType, AttachmentType.SCOPE);
    }

    /**
     * Server-safe (coarse) check: whether a slot's {@code allow} categories on a host of {@code hostType}
     * admit a mount of {@code mountType}. Cannot tell scope from sight (client display data) — both map to
     * {@code SCOPE} — but distinguishes {@code SCOPE} vs {@code LASER} etc., which is enough for server
     * authorization. Enforces the firm rule that <b>optics (SCOPE-type) may only nest on SCOPE hosts</b>:
     * a non-scope host never accepts a SCOPE mount, even under {@code allow: "any"}.
     */
    public static boolean typeAllowed(List<String> allow, AttachmentType mountType, AttachmentType hostType) {
        if (hostType != AttachmentType.SCOPE && mountType == AttachmentType.SCOPE) return false;
        for (String category : allow) {
            if (RailsModifier.RailSlot.ALLOW_ANY.equals(category)) return true;
            if (categoryType(category) == mountType) return true;
        }
        return false;
    }
}
