package edu.whimc.overworld_agent.utils;

import net.citizensnpcs.util.MojangSkinGenerator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;

import java.util.function.Consumer;

/**
 * Fetches player skin texture data from a public image URL via Citizens' Mineskin integration
 * (same backend as {@code /npc skin --url}).
 */
public final class CitizensSkinUrls {

    public record SkinData(String cacheId, String signature, String texture) {
    }

    private CitizensSkinUrls() {
    }

    public static boolean isHttpsUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.regionMatches(true, 0, "https://", 0, 8);
    }

    public static void fetchFromUrl(
            Plugin plugin,
            String url,
            boolean slim,
            Consumer<SkinData> onSuccess,
            Consumer<String> onFailure
    ) {
        String trimmedUrl = url.trim();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JSONObject data = MojangSkinGenerator.generateFromURL(trimmedUrl, slim);
                String cacheId = (String) data.get("uuid");
                JSONObject texture = (JSONObject) data.get("texture");
                String textureEncoded = (String) texture.get("value");
                String signature = (String) texture.get("signature");

                if (cacheId == null || textureEncoded == null || signature == null) {
                    throw new IllegalStateException("Mineskin returned incomplete skin data.");
                }

                SkinData skinData = new SkinData(cacheId, signature, textureEncoded);
                Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(skinData));
            } catch (Throwable t) {
                String message = t.getMessage();
                if (message == null || message.isBlank()) {
                    message = t.getClass().getSimpleName();
                }
                String finalMessage = message;
                Bukkit.getScheduler().runTask(plugin, () -> onFailure.accept(finalMessage));
            }
        });
    }
}
