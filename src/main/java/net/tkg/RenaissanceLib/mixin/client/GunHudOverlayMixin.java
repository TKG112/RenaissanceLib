package net.tkg.RenaissanceLib.mixin.client;

import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.BinaryFireMode;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;
import net.tkg.RenaissanceLib.client.underbarrel.UnderbarrelClient;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adapts the gun ammo HUD for RenaissanceLib features:
 * <ul>
 *   <li>Draws the binary fire-mode icon (TaC:Z falls back to the SEMI texture for binary).</li>
 *   <li>When the underbarrel is the active weapon, shows <em>its</em> ammo — current / magazine / spare
 *       inventory — instead of the host gun's, by swapping the values the HUD reads. All swaps are gated on
 *       the underbarrel being active, so the host gun's HUD is unchanged.</li>
 * </ul>
 */
@Mixin(value = GunHudOverlay.class, remap = false)
public class GunHudOverlayMixin {
    private static final ResourceLocation BINARY_TEXTURE =
            new ResourceLocation(RenaissanceLibMod.MOD_ID, "textures/hud/fire_mode_binary.png");

    @Shadow
    private static int cacheMaxAmmoCount;

    @Shadow
    private static int cacheInventoryAmmoCount;

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V",
                    ordinal = 1,
                    remap = true),
            index = 0)
    private ResourceLocation renaissance$binaryFireModeIcon(ResourceLocation original) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return original;
        ItemStack held = player.getMainHandItem();
        // While the underbarrel is active, its own binary selection drives the icon; otherwise the host gun's.
        GunData ub = renaissance$activeUnderbarrel(held);
        boolean binary = ub != null
                ? UnderbarrelFireMode.isBinary(held, ub)
                : BinaryFireMode.isActive(held);
        return binary ? BINARY_TEXTURE : original;
    }

    // ---- underbarrel HUD gun image ----------------------------------------------------------------------

    /** The gun image: the underbarrel's own HUD texture when it's active (if it declares one), else the host's. */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/resource/GunDisplayInstance;getHUDTexture()Lnet/minecraft/resources/ResourceLocation;"),
            remap = false)
    private ResourceLocation renaissance$hudTexture(GunDisplayInstance display) {
        ResourceLocation ub = renaissance$underbarrelHudTexture(false);
        return ub != null ? ub : display.getHUDTexture();
    }

    /** The empty-mag gun image: the underbarrel's own when active (if declared), else the host's. */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/client/resource/GunDisplayInstance;getHudEmptyTexture()Lnet/minecraft/resources/ResourceLocation;"),
            remap = false)
    private ResourceLocation renaissance$hudEmptyTexture(GunDisplayInstance display) {
        ResourceLocation ub = renaissance$underbarrelHudTexture(true);
        return ub != null ? ub : display.getHudEmptyTexture();
    }

    /**
     * The active underbarrel's HUD texture (the {@code hud} / {@code hud_empty} in its {@code underbarrel_display}),
     * or {@code null} if the underbarrel isn't active or didn't declare one — the underbarrel display uses raw
     * texture paths (it isn't {@code init()}-converted), matching its muzzle-flash convention.
     */
    private static ResourceLocation renaissance$underbarrelHudTexture(boolean empty) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack held = player.getMainHandItem();
        if (!ActiveWeapon.isUnderbarrelActive(held)) return null;
        GunDisplay display = UnderbarrelClient.getUnderbarrelDisplay(Underbarrel.getInstalledUnderbarrel(held));
        if (display == null) return null;
        return empty ? display.getHudEmptyTextureLocation() : display.getHudTextureLocation();
    }

    // ---- underbarrel ammo display -----------------------------------------------------------------------

    /** Current loaded rounds: the underbarrel's when it's active, else the host gun's. */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/item/IGun;getCurrentAmmoCount(Lnet/minecraft/world/item/ItemStack;)I"),
            remap = false)
    private int renaissance$currentAmmo(IGun iGun, ItemStack gunItem) {
        GunData ub = renaissance$activeUnderbarrel(gunItem);
        return ub != null ? UnderbarrelAmmo.get(gunItem, ub) : iGun.getCurrentAmmoCount(gunItem);
    }

    /** The underbarrel tracks all its rounds in one count — suppress the host gun's "+1 in barrel" bump. */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/api/item/IGun;hasBulletInBarrel(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    private boolean renaissance$hasBulletInBarrel(IGun iGun, ItemStack gunItem) {
        if (renaissance$activeUnderbarrel(gunItem) != null) return false;
        return iGun.hasBulletInBarrel(gunItem);
    }

    /** Magazine capacity: the underbarrel's when active, else the host's cached value. */
    @Redirect(
            method = "render",
            at = @At(value = "FIELD",
                    target = "Lcom/tacz/guns/client/gui/overlay/GunHudOverlay;cacheMaxAmmoCount:I",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private int renaissance$maxAmmo() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return cacheMaxAmmoCount;
        ItemStack held = player.getMainHandItem();
        GunData ub = renaissance$activeUnderbarrel(held);
        return ub != null ? UnderbarrelAmmo.maxAmmo(held, ub) : cacheMaxAmmoCount;
    }

    /** Spare ammo in inventory: matching underbarrel rounds when active, else the host's cached value. */
    @Redirect(
            method = "render",
            at = @At(value = "FIELD",
                    target = "Lcom/tacz/guns/client/gui/overlay/GunHudOverlay;cacheInventoryAmmoCount:I",
                    opcode = Opcodes.GETSTATIC),
            remap = false)
    private int renaissance$inventoryAmmo() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return cacheInventoryAmmoCount;
        GunData ub = renaissance$activeUnderbarrel(player.getMainHandItem());
        if (ub == null || ub.getAmmoId() == null) return cacheInventoryAmmoCount;
        return renaissance$countInventoryAmmo(player, ub.getAmmoId());
    }

    /** The underbarrel {@link GunData} if it's the active weapon on the given gun, else {@code null}. */
    private static GunData renaissance$activeUnderbarrel(ItemStack gunItem) {
        if (!ActiveWeapon.isUnderbarrelActive(gunItem)) return null;
        return Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gunItem));
    }

    private static int renaissance$countInventoryAmmo(LocalPlayer player, ResourceLocation ammoId) {
        // Creative = unlimited, mirroring the reload's own creative handling.
        if (player.getAbilities().instabuild) return 9999;

        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            IAmmo iAmmo = IAmmo.getIAmmoOrNull(stack);
            if (iAmmo != null && ammoId.equals(iAmmo.getAmmoId(stack))) {
                total += stack.getCount();
                continue;
            }
            if (stack.getItem() instanceof IAmmoBox box) {
                if (box.isAllTypeCreative(stack)
                        || (box.isCreative(stack) && ammoId.equals(box.getAmmoId(stack)))) {
                    return 9999;
                }
                if (ammoId.equals(box.getAmmoId(stack))) {
                    total += box.getAmmoCount(stack);
                }
            }
        }
        return Math.min(9999, total);
    }
}
