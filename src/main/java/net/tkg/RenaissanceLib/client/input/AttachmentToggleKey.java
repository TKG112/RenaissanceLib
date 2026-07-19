package net.tkg.RenaissanceLib.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.AttachmentStates;
import net.tkg.RenaissanceLib.attachment.AttachmentStatesModifier;
import net.tkg.RenaissanceLib.network.ClientMessageToggleAttachment;
import net.tkg.RenaissanceLib.network.NetworkHandler;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RenaissanceLibMod.MOD_ID, value = Dist.CLIENT)
public class AttachmentToggleKey {
    public static final Map<AttachmentType, KeyMapping> KEYS = new EnumMap<>(AttachmentType.class);

    static {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            KEYS.put(type, new KeyMapping(
                    "key.renaissance_lib.toggle_attachment." + type.name().toLowerCase(java.util.Locale.ENGLISH) + ".desc",
                    KeyConflictContext.IN_GAME,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    "key.category.renaissance_lib"));
        }
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        for (Map.Entry<AttachmentType, KeyMapping> entry : KEYS.entrySet()) {
            KeyMapping key = entry.getValue();

            if (key.isUnbound()) continue;
            if (key.matches(event.getKey(), event.getScanCode())) {
                doToggle(entry.getKey());
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMousePress(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        for (Map.Entry<AttachmentType, KeyMapping> entry : KEYS.entrySet()) {
            KeyMapping key = entry.getValue();
            if (key.isUnbound()) continue;
            if (key.matchesMouse(event.getButton())) {
                doToggle(entry.getKey());
                return;
            }
        }
    }

    private static void doToggle(AttachmentType type) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || mc.level == null) return;

        ItemStack gunItem = player.getMainHandItem();
        if (IGun.getIGunOrNull(gunItem) == null) return;

        AttachmentStatesModifier.States states = AttachmentStates.getStates(gunItem, type);
        if (states == null || states.getCycle().size() < 2) return;

        String next = states.next(AttachmentStates.getState(gunItem, type));

        AttachmentStates.setState(gunItem, type, next);

        AttachmentStates.applyZoomForState(gunItem, type, next);

        String animation = states.getAnimation(next);
        if (animation != null && !animation.isEmpty()) {
            triggerAnimation(gunItem, animation);
        }

        NetworkHandler.CHANNEL.sendToServer(new ClientMessageToggleAttachment(type));
    }

    private static void triggerAnimation(ItemStack gunItem, String input) {
        try {
            TimelessAPI.getGunDisplay(gunItem).ifPresent(display -> {
                var stateMachine = display.getAnimationStateMachine();
                if (stateMachine != null) {
                    stateMachine.trigger(input);
                }
            });
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Failed to trigger animation '{}'", input, t);
        }
    }
}
