package net.tkg.RenaissanceLib.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ShaderSpec {
    @Nullable
    private final ResourceLocation all;
    private final Map<Integer, ResourceLocation> perView;

    private ShaderSpec(@Nullable ResourceLocation all, Map<Integer, ResourceLocation> perView) {
        this.all = all;
        this.perView = perView;
    }

    @Nullable
    public static ShaderSpec parse(@Nullable JsonElement element) {
        if (element == null) return null;

        if (element.isJsonPrimitive()) {
            ResourceLocation location = toLocation(element.getAsString());
            return location == null ? null : new ShaderSpec(location, Map.of());
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            Map<Integer, ResourceLocation> perView = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                Integer view = toInt(entry.getKey());
                ResourceLocation location = toLocation(entry.getValue().getAsString());
                if (view != null && location != null) perView.put(view, location);
            }
            return perView.isEmpty() ? null : new ShaderSpec(null, perView);
        }

        return null;
    }

    @Nullable
    public ResourceLocation forView(int viewIndex) {
        if (all != null) return all;
        return perView.get(viewIndex);
    }

    @Nullable
    private static ResourceLocation toLocation(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        return name.contains(":")
                ? ResourceLocation.tryParse(name)
                : ResourceLocation.tryParse("minecraft:" + name);
    }

    @Nullable
    private static Integer toInt(String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShaderSpec other)) return false;
        return Objects.equals(all, other.all) && perView.equals(other.perView);
    }

    @Override
    public int hashCode() {
        return Objects.hash(all, perView);
    }

    @Override
    public String toString() {
        return all != null ? all.toString() : perView.toString();
    }
}
