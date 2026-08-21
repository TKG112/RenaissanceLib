package net.tkg.RenaissanceLib.client;

import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.tkg.RenaissanceLib.attachment.RailsModifier;

import java.util.List;

/**
 * Wires a rail-capable scope's model so each declared rail slot renders whatever sight is mounted in it.
 *
 * <p>Called once, when the scope's {@code ClientAttachmentIndex} finishes loading its model (see the
 * corresponding mixin). For each {@code rails.slots[i]} whose {@code node} exists in the model, we hang a
 * {@link RailSightRenderer} for rail index {@code i} on that node — so it draws at the node's transform
 * (the canted/top mount position) during the scope's normal render.
 */
public final class RailRenderRegistrar {
    private RailRenderRegistrar() {}

    public static void register(AttachmentData data, BedrockAttachmentModel model) {
        if (data == null || model == null) return;
        JsonProperty<?> property = data.getModifier().get(RailsModifier.ID);
        if (property == null || !(property.getValue() instanceof RailsModifier.Spec spec)) return;

        List<RailsModifier.RailSlot> slots = spec.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            String node = slots.get(i).getNode();
            if (model.getNode(node) == null) {
                RenaissanceLibMod.LOGGER.warn(
                        "[RenaissanceLib] Rail slot {} references node '{}' missing from the scope model; skipping.",
                        i, node);
                continue;
            }
            int index = i;
            model.setFunctionalRenderer(node, part -> new RailSightRenderer(index, model));
        }
    }
}
