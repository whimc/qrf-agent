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
 * Collects text from chat (more capacity than a sign). Paper 1.21.11+ requires
 * {@code written_book_content} for {@link Player#openBook}, so writable-book UIs no longer work for this use case.
 * Supports one-shot prompts ({@link #open}) and ongoing chat sessions ({@link #openSession}) where every
 * chat line is routed to the callback until the player types {@code stop} or {@code exit}.
 */
public final class ChatTextInputFactory implements Listener {

    private record PendingInput(Consumer<String> callback, boolean session) {
    }

    private final Plugin plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

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
        pending.put(player.getUniqueId(), new PendingInput(onSubmit, false));
        for (String line : instructionLines) {
            player.sendMessage(line);
        }
        player.sendMessage(ChatColor.GRAY + "Type your reply in chat.");
        player.sendMessage(ChatColor.DARK_GRAY + "Type " + ChatColor.WHITE + "cancel" + ChatColor.DARK_GRAY + " to abort.");
    }

    /**
     * Starts an ongoing chat session: every chat line the player sends is passed to {@code onMessage}
     * until they type {@code stop} or {@code exit} (which ends the session and notifies them).
     */
    public void openSession(Player player, List<String> instructionLines, Consumer<String> onMessage) {
        if (!player.isOnline()) {
            return;
        }
        pending.put(player.getUniqueId(), new PendingInput(onMessage, true));
        for (String line : instructionLines) {
            player.sendMessage(line);
        }
        player.sendMessage(ChatColor.GRAY + "AI chat mode is on. Anything you type in chat is sent to your agent.");
        player.sendMessage(ChatColor.DARK_GRAY + "Type " + ChatColor.WHITE + "stop" + ChatColor.DARK_GRAY + " or "
                + ChatColor.WHITE + "exit" + ChatColor.DARK_GRAY + " to end the chat.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput input = pending.get(player.getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (input.session()) {
            if (isSessionExit(plain)) {
                pending.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.YELLOW + "AI chat mode ended.");
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    input.callback().accept(plain);
                }
            });
            return;
        }

        pending.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if ("cancel".equalsIgnoreCase(plain)) {
                player.sendMessage(ChatColor.RED + "Cancelled.");
                return;
            }
            input.callback().accept(plain);
        });
    }

    private static boolean isSessionExit(String message) {
        return "stop".equalsIgnoreCase(message) || "exit".equalsIgnoreCase(message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
