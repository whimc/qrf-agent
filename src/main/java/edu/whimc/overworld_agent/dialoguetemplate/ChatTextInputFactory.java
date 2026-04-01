package edu.whimc.overworld_agent.dialoguetemplate;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Collects a line of text from chat (more capacity than a sign). Paper 1.21.11+ requires
 * {@code written_book_content} for {@link Player#openBook}, so writable-book UIs no longer work for this use case.
 */
public final class ChatTextInputFactory implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatTextInputFactory(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** @param contentStartPage ignored (kept for API compatibility with former book flow). */
    public void open(Player player, List<String> instructionLines, Consumer<String> onSubmit) {
        open(player, instructionLines, 1, onSubmit);
    }

    public void open(Player player, List<String> instructionLines, int contentStartPage, Consumer<String> onSubmit) {
        if (!player.isOnline()) {
            return;
        }
        pending.put(player.getUniqueId(), onSubmit);
        for (String line : instructionLines) {
            player.sendMessage(line);
        }
        player.sendMessage(ChatColor.GRAY + "Type your reply in chat.");
        player.sendMessage(ChatColor.DARK_GRAY + "Type " + ChatColor.WHITE + "cancel" + ChatColor.DARK_GRAY + " to abort.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = pending.get(player.getUniqueId());
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        pending.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if ("cancel".equalsIgnoreCase(plain)) {
                player.sendMessage(ChatColor.RED + "Cancelled.");
                return;
            }
            callback.accept(plain);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
