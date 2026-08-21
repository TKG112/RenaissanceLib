package net.tkg.RenaissanceLib.client.underbarrel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelAmmo;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders an underbarrel attachment's sub-gun stats as a gun-style tooltip block — the ammo item icon with the
 * ammo name beside it, then damage (+ explosion), fire rate and fire modes — mirroring TaC:Z's
 * {@code ClientGunTooltip} layout and colours so it reads exactly like a gun's description. Fed by
 * {@link UnderbarrelStatsTooltip}; registered in {@code RenaissanceLibMod.ClientModEvents}.
 */
@OnlyIn(Dist.CLIENT)
public class ClientUnderbarrelStatsTooltip implements ClientTooltipComponent {
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("0.#");
    private static final int LABEL_COLOR = 0x777777;
    /** Height of the ammo row (icon with name over capacity). */
    private static final int AMMO_ROW = 24;
    /** Horizontal space left for the ammo icon before the name/capacity text. */
    private static final int NAME_INDENT = 20;
    private static final int LINE = 10;

    private final ItemStack ammo;
    private final MutableComponent header;
    private final MutableComponent ammoName;
    private final MutableComponent capacity;
    private final List<MutableComponent> stats = new ArrayList<>();

    public ClientUnderbarrelStatsTooltip(UnderbarrelStatsTooltip tooltip) {
        GunData data = tooltip.getData();

        this.header = Component.translatable("tooltip.renaissance_lib.underbarrel.header")
                .withStyle(ChatFormatting.GOLD);

        ResourceLocation ammoId = data.getAmmoId();
        this.ammo = ammoId != null ? AmmoItemBuilder.create().setId(ammoId).build() : ItemStack.EMPTY;
        String ammoNameKey = ammoId == null ? "" : TimelessAPI.getClientAmmoIndex(ammoId)
                .map(index -> index.getName()).orElse(ammoId.getPath());
        this.ammoName = Component.translatable(ammoNameKey).withStyle(ChatFormatting.GOLD);

        // Ammo count under the name, gun-tooltip style ("current/max"). A loose inventory item has no loaded
        // state, so it reads full; but in the refit screen (which refits the held gun) we show the installed
        // underbarrel's live count, so it updates as you fire it.
        int max = data.getAmmoAmount();
        int current = max;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GunRefitScreen && mc.player != null) {
            ItemStack heldGun = mc.player.getMainHandItem();
            if (Underbarrel.hasUnderbarrel(heldGun)) {
                current = UnderbarrelAmmo.get(heldGun, data);
            }
        }
        this.capacity = Component.literal(current + "/" + max);

        // Damage (+ explosion), presented like the gun tooltip: gray label, coloured value.
        BulletData bullet = data.getBulletData();
        if (bullet != null) {
            double multiplier = SyncConfig.DAMAGE_BASE_MULTIPLIER.get();
            int pellets = bullet.getBulletAmount();
            double damage = bullet.getDamageAmount() * multiplier;
            String damageText = pellets > 1
                    ? DAMAGE_FORMAT.format(damage) + "x" + pellets
                    : DAMAGE_FORMAT.format(damage);
            MutableComponent value = Component.literal(damageText).withStyle(ChatFormatting.AQUA);
            ExplosionData explosion = bullet.getExplosionData();
            if (explosion != null) {
                value.append(Component.literal(" + " + DAMAGE_FORMAT.format(explosion.getDamage() * multiplier)))
                        .append(Component.translatable("tooltip.tacz.gun.explosion"));
            }
            stats.add(Component.translatable("tooltip.tacz.gun.damage").append(value));
        }

        stats.add(Component.translatable("tooltip.renaissance_lib.underbarrel.rpm")
                .append(Component.literal(String.valueOf(data.getRoundsPerMinute())).withStyle(ChatFormatting.AQUA)));

        List<FireMode> modes = data.getFireModeSet();
        if (modes != null && !modes.isEmpty()) {
            MutableComponent value = Component.empty();
            for (int i = 0; i < modes.size(); i++) {
                if (i > 0) value.append(", ");
                value.append(fireModeName(modes.get(i)));
            }
            stats.add(Component.translatable("tooltip.renaissance_lib.underbarrel.fire_modes")
                    .append(value.withStyle(ChatFormatting.AQUA)));
        }
    }

    /** TaC:Z's own translated fire-mode name, falling back to a capitalized enum name if a key is missing. */
    private static Component fireModeName(FireMode mode) {
        switch (mode) {
            case AUTO:
                return Component.translatable("gui.tacz.gun_refit.property_diagrams.auto");
            case SEMI:
                return Component.translatable("gui.tacz.gun_refit.property_diagrams.semi");
            case BURST:
                return Component.translatable("gui.tacz.gun_refit.property_diagrams.burst");
            default:
                String name = mode.name().toLowerCase();
                return Component.literal(name.substring(0, 1).toUpperCase() + name.substring(1));
        }
    }

    @Override
    public int getHeight() {
        // header + ammo row (icon with name over capacity) + one line per stat.
        return LINE + AMMO_ROW + stats.size() * LINE + 2;
    }

    @Override
    public int getWidth(Font font) {
        int width = Math.max(font.width(header), NAME_INDENT + Math.max(font.width(ammoName), font.width(capacity)));
        for (MutableComponent stat : stats) {
            width = Math.max(width, font.width(stat));
        }
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffers) {
        int yOffset = y;
        font.drawInBatch(header, x, yOffset, LABEL_COLOR, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        yOffset += LINE;

        // Ammo name over capacity, beside the icon (icon drawn in renderImage at x).
        font.drawInBatch(ammoName, x + NAME_INDENT, yOffset + 3, LABEL_COLOR, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        font.drawInBatch(capacity, x + NAME_INDENT, yOffset + 13, LABEL_COLOR, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        yOffset += AMMO_ROW;

        for (MutableComponent stat : stats) {
            font.drawInBatch(stat, x, yOffset, LABEL_COLOR, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            yOffset += LINE;
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        if (!ammo.isEmpty()) {
            // Below the header line, aligned with the ammo name/capacity row.
            guiGraphics.renderItem(ammo, x, y + LINE + 2);
        }
    }
}
