package net.tkg.RenaissanceLib;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client config for RenaissanceLib. Registered in {@link RenaissanceLibMod}'s constructor.
 */
public final class RenaissanceConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    private RenaissanceConfig() {}

    public static final class Client {
        /**
         * Attachment wheel interaction style. {@code false} (default): press the key to open — the wheel
         * stays open and you point + left-click an attachment to toggle it. {@code true}: hold the key to
         * open, point, and release on an attachment to toggle it.
         */
        public final ForgeConfigSpec.BooleanValue holdToOpenWheel;

        Client(ForgeConfigSpec.Builder b) {
            b.push("attachment_wheel");
            holdToOpenWheel = b
                    .comment(
                            "How the attachment wheel is operated.",
                            "false (default): press to open; the wheel stays open and you point + left-click an attachment to toggle it.",
                            "true: hold to open; point while holding, release on an attachment to toggle it.")
                    .define("holdToOpen", false);
            b.pop();
        }
    }
}
