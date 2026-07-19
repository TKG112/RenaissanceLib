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
    }
}
