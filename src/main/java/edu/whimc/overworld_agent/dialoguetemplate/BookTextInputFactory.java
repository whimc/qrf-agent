package edu.whimc.overworld_agent.dialoguetemplate;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Opens a {@link Material#WRITABLE_BOOK} editor for multi-line input (more space than a sign).
 */
public final class BookTextInputFactory implements Listener {

    private final Plugin plugin;

    /**
     * @param contentStartPage first page index (0-based) counted as user content; earlier pages are instructions only.
     */
    private record Pending(int contentStartPage, Consumer<String> callback) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public BookTextInputFactory(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Page 0 = instructions; page 1+ = user content (default).
     */
    public void open(Player player, List<String> instructionLines, Consumer<String> onSubmit) {
        open(player, instructionLines, 1, onSubmit);
    }

    public void open(Player player, List<String> instructionLines, int contentStartPage, Consumer<String> onSubmit) {
        if (!player.isOnline()) {
            return;
        }
        pending.put(player.getUniqueId(), new Pending(Math.max(0, contentStartPage), onSubmit));

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) {
            pending.remove(player.getUniqueId());
            return;
        }
        meta.setAuthor(plugin.getName());
        meta.setTitle("Overworld Agent");
        List<String> pages = new ArrayList<>(instructionLines);
        pages.add("");
        meta.setPages(pages);
        book.setItemMeta(meta);

        Player p = player;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                p.openBook(book);
            }
        });
    }

    @EventHandler
    public void onEditBook(PlayerEditBookEvent event) {
        Pending pend = pending.remove(event.getPlayer().getUniqueId());
        if (pend == null) {
            return;
        }
        String text = extractPlain(event.getNewBookMeta(), pend.contentStartPage);
        text = text.trim().replace('\n', ' ').replaceAll("\\s+", " ");
        pend.callback.accept(text);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private static String extractPlain(BookMeta meta, int contentStartPage) {
        if (meta == null) {
            return "";
        }
        List<String> legacy = meta.getPages();
        if (legacy != null && !legacy.isEmpty()) {
            return joinPages(legacy, contentStartPage);
        }
        try {
            List<net.kyori.adventure.text.Component> pages = meta.pages();
            if (pages != null && !pages.isEmpty()) {
                PlainTextComponentSerializer ser = PlainTextComponentSerializer.plainText();
                List<String> strings = new ArrayList<>(pages.size());
                for (net.kyori.adventure.text.Component c : pages) {
                    strings.add(ser.serialize(c));
                }
                return joinPages(strings, contentStartPage);
            }
        } catch (Throwable ignored) {
            // Older BookMeta without adventure pages
        }
        return "";
    }

    private static String joinPages(List<String> pages, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, start); i < pages.size(); i++) {
            String p = pages.get(i);
            if (p == null || p.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(p.replace('\n', ' ').trim());
        }
        return sb.toString();
    }
}
