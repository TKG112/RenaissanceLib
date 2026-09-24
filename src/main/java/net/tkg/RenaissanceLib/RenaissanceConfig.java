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

        /** Refit screen: floating slot cards instead of TaC:Z's slot buttons and list (read when the screen opens). */
        public final ForgeConfigSpec.BooleanValue refitCards;
        /** Refit screen: background blur radius, 0 = off (read every frame). */
        public final ForgeConfigSpec.IntValue refitBlur;
        /** Refit screen: rotate / move / zoom the gun with the mouse (read when the screen opens). */
        public final ForgeConfigSpec.BooleanValue refitFreeCamera;

        Client(ForgeConfigSpec.Builder b) {
            b.push("attachment_wheel");
            holdToOpenWheel = b
                    .comment(
                            "How the attachment wheel is operated.",
                            "false (default): press to open; the wheel stays open and you point + left-click an attachment to toggle it.",
                            "true: hold to open; point while holding, release on an attachment to toggle it.")
                    .define("holdToOpen", false);
            b.pop();

            b.push("refit_screen");
            refitCards = b
                    .comment(
                            "Floating slot cards around the gun (click a card to pick its attachments), in place of",
                            "TaC:Z's slot buttons and attachment list. false (default): TaC:Z's layout.",
                            "Takes effect the next time the refit screen opens.")
                    .define("slotCards", false);
            refitBlur = b
                    .comment(
                            "How strongly the world behind the refit screen is blurred. 0 turns the blur off.",
                            "Never applied while an Oculus shader pack is active.")
                    .defineInRange("backgroundBlur", 12, 0, 24);
            refitFreeCamera = b
                    .comment(
                            "Turn (left-drag), move (right-drag) and zoom (scroll) the gun in the refit screen;",
                            "double-click or R resets. false: the gun stays in TaC:Z's fixed views.",
                            "Takes effect the next time the refit screen opens.")
                    .define("freeCamera", true);
            b.pop();
        }
    }
}
