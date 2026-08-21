package net.tkg.RenaissanceLib.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tkg.RenaissanceLib.RenaissanceLibMod;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RenaissanceLibMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int id = 0;

    private NetworkHandler() {}

    public static void register() {
        CHANNEL.registerMessage(id++, ClientMessageToggleAttachment.class,
                ClientMessageToggleAttachment::encode,
                ClientMessageToggleAttachment::decode,
                ClientMessageToggleAttachment::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetRailSight.class,
                ClientMessageSetRailSight::encode,
                ClientMessageSetRailSight::decode,
                ClientMessageSetRailSight::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetRailLaserColor.class,
                ClientMessageSetRailLaserColor::encode,
                ClientMessageSetRailLaserColor::decode,
                ClientMessageSetRailLaserColor::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetActiveWeapon.class,
                ClientMessageSetActiveWeapon::encode,
                ClientMessageSetActiveWeapon::decode,
                ClientMessageSetActiveWeapon::handle);
        CHANNEL.registerMessage(id++, ClientMessageFireUnderbarrel.class,
                ClientMessageFireUnderbarrel::encode,
                ClientMessageFireUnderbarrel::decode,
                ClientMessageFireUnderbarrel::handle);
        CHANNEL.registerMessage(id++, ClientMessageReloadUnderbarrel.class,
                ClientMessageReloadUnderbarrel::encode,
                ClientMessageReloadUnderbarrel::decode,
                ClientMessageReloadUnderbarrel::handle);
        CHANNEL.registerMessage(id++, ClientMessageCycleUnderbarrelFireMode.class,
                ClientMessageCycleUnderbarrelFireMode::encode,
                ClientMessageCycleUnderbarrelFireMode::decode,
                ClientMessageCycleUnderbarrelFireMode::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetConversionKit.class,
                ClientMessageSetConversionKit::encode,
                ClientMessageSetConversionKit::decode,
                ClientMessageSetConversionKit::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetFireMode.class,
                ClientMessageSetFireMode::encode,
                ClientMessageSetFireMode::decode,
                ClientMessageSetFireMode::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetUnderbarrelFireMode.class,
                ClientMessageSetUnderbarrelFireMode::encode,
                ClientMessageSetUnderbarrelFireMode::decode,
                ClientMessageSetUnderbarrelFireMode::handle);
        CHANNEL.registerMessage(id++, ClientMessageSetUnderbarrelAttachment.class,
                ClientMessageSetUnderbarrelAttachment::encode,
                ClientMessageSetUnderbarrelAttachment::decode,
                ClientMessageSetUnderbarrelAttachment::handle);
        CHANNEL.registerMessage(id++, ServerMessageUnderbarrelSound.class,
                ServerMessageUnderbarrelSound::encode,
                ServerMessageUnderbarrelSound::decode,
                ServerMessageUnderbarrelSound::handle);
    }
}
