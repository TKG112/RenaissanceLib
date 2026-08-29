package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.ActiveWeapon;
import net.tkg.RenaissanceLib.attachment.AttachmentOverrides;
import net.tkg.RenaissanceLib.attachment.BinaryFireMode;
import net.tkg.RenaissanceLib.attachment.Underbarrel;
import net.tkg.RenaissanceLib.attachment.UnderbarrelFireMode;
import net.tkg.RenaissanceLib.network.ClientMessageSetActiveWeapon;
import net.tkg.RenaissanceLib.network.ClientMessageSetFireMode;
import net.tkg.RenaissanceLib.network.ClientMessageSetUnderbarrelFireMode;
import net.tkg.RenaissanceLib.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The fire-mode radial wheel — opened by holding the fire-select key (a tap still cycles; see
 * {@code FireSelectInput}). It lists the <em>active</em> weapon's fire modes (the host gun's, or the
 * underbarrel's when it's active), plus the binary pseudo-mode when that weapon supports it. Picking a mode
 * sets it directly (server-authoritative), which TaC:Z's cycle-only fire-select can't do.
 *
 * <p>Reuses the shared {@link RadialWheelState}/{@link RadialRing} visuals like the attachment wheel.
 */
@OnlyIn(Dist.CLIENT)
public final class FireModeWheel {

    public static final ResourceLocation SEMI_ICON = new ResourceLocation("tacz", "textures/hud/fire_mode_semi.png");
    public static final ResourceLocation AUTO_ICON = new ResourceLocation("tacz", "textures/hud/fire_mode_auto.png");
    public static final ResourceLocation BURST_ICON = new ResourceLocation("tacz", "textures/hud/fire_mode_burst.png");
    public static final ResourceLocation BINARY_ICON =
            new ResourceLocation(RenaissanceLibMod.MOD_ID, "textures/hud/fire_mode_binary.png");

    /**
     * One selectable fire mode. {@code ubIndex >= 0} = an underbarrel cycle index to set; {@code -1} = a host
     * gun mode set by {@code mode} + {@code binary}.
     */
    public record Choice(FireMode mode, boolean binary, int ubIndex, ResourceLocation icon, Component label) {}

    private static final RadialWheelState<Choice> STATE = new RadialWheelState<>(FireModeWheel::apply);

    private FireModeWheel() {}

    public static boolean isOpen() {
        return STATE.isOpen();
    }

    public static boolean isRendering() {
        return STATE.isRendering();
    }

    public static float renderAlpha() {
        return STATE.renderAlpha();
    }

    public static double pointerAngleDeg() {
        return STATE.pointerAngleDeg();
    }

    public static List<Choice> choices() {
        return STATE.items();
    }

    public static int highlighted() {
        return STATE.highlighted();
    }

    public static int openSlot() {
        return STATE.openSlot();
    }

    public static void feedLook(double dx, double dy) {
        STATE.feedLook(dx, dy);
    }

    public static void confirm() {
        STATE.confirm();
    }

    public static void close() {
        STATE.close();
    }

    /**
     * Open the wheel for the held gun. Lists the host gun's fire modes and — when an underbarrel is installed —
     * the underbarrel's modes in the same ring, so the wheel doubles as a weapon selector: picking an
     * underbarrel segment switches to (and arms) the underbarrel, picking a host segment switches back.
     * Returns {@code false} unless there are 2+ choices.
     */
    public static boolean tryOpen() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return false;

        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;

        List<Choice> choices = new ArrayList<>(hostChoices(gun, iGun));
        if (Underbarrel.hasUnderbarrel(gun)) {
            choices.addAll(underbarrelChoices(gun));
        }
        if (choices.size() < 2) return false; // nothing worth a radial

        return STATE.open(choices, player.getInventory().selected);
    }

    private static List<Choice> hostChoices(ItemStack gun, IGun iGun) {
        List<Choice> choices = new ArrayList<>();
        GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData()).orElse(null);
        if (gunData == null) return choices;
        for (FireMode mode : AttachmentOverrides.effectiveFireModes(gun, gunData)) {
            choices.add(new Choice(mode, false, -1, iconFor(mode), labelFor(mode)));
        }
        if (AttachmentOverrides.isBinaryCapable(gun, gunData)) {
            choices.add(new Choice(FireMode.SEMI, true, -1, BINARY_ICON, binaryLabel()));
        }
        return choices;
    }

    private static List<Choice> underbarrelChoices(ItemStack gun) {
        List<Choice> choices = new ArrayList<>();
        GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
        if (ubData == null) return choices;
        List<FireMode> modes = UnderbarrelFireMode.getModes(ubData);
        int binaryPos = UnderbarrelFireMode.binaryPosition(ubData);
        int cycleSize = modes.size() + (binaryPos >= 0 ? 1 : 0);
        // Walk the cycle in order, inserting binary at its authored position so the wheel matches the cycle.
        int modeIndex = 0;
        for (int i = 0; i < cycleSize; i++) {
            if (i == binaryPos) {
                choices.add(new Choice(FireMode.SEMI, true, i, BINARY_ICON, binaryLabel()));
            } else {
                FireMode mode = modes.get(modeIndex++);
                choices.add(new Choice(mode, false, i, iconFor(mode), labelFor(mode)));
            }
        }
        return choices;
    }

    private static void apply(Choice choice) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return;

        if (choice.ubIndex() >= 0) {
            GunData ubData = Underbarrel.getUnderbarrelData(Underbarrel.getInstalledUnderbarrel(gun));
            if (ubData == null) return;
            // Arm the underbarrel first (if it isn't already), so picking one of its modes also switches to it.
            if (!ActiveWeapon.isUnderbarrelActive(gun)) {
                ActiveWeapon.set(gun, ActiveWeapon.UNDERBARREL); // client prediction; server re-validates
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetActiveWeapon(ActiveWeapon.UNDERBARREL));
            }
            UnderbarrelFireMode.setIndex(gun, ubData, choice.ubIndex()); // client prediction
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetUnderbarrelFireMode(choice.ubIndex()));
        } else {
            // Switch back to the host gun (if the underbarrel was active) and set the chosen host mode.
            if (ActiveWeapon.isUnderbarrelActive(gun)) {
                ActiveWeapon.set(gun, ActiveWeapon.MAIN); // client prediction; server re-validates
                NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetActiveWeapon(ActiveWeapon.MAIN));
            }
            iGun.setFireMode(gun, choice.mode()); // client prediction
            BinaryFireMode.setActive(gun, choice.binary());
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageSetFireMode(choice.mode(), choice.binary()));
        }

        // The radial sets the mode directly (not via TaC:Z's fireSelect), so play the change click ourselves.
        TimelessAPI.getGunDisplay(gun).ifPresent(display -> SoundPlayManager.playFireSelectSound(player, display));
    }

    public static ResourceLocation iconFor(FireMode mode) {
        return switch (mode) {
            case AUTO -> AUTO_ICON;
            case BURST -> BURST_ICON;
            default -> SEMI_ICON;
        };
    }

    private static Component labelFor(FireMode mode) {
        String key = "gui.tacz.gun_refit.property_diagrams." + mode.name().toLowerCase();
        return Component.translatable(key);
    }

    private static Component binaryLabel() {
        return Component.translatable("tooltip.renaissance_lib.fire_mode.binary");
    }
}
