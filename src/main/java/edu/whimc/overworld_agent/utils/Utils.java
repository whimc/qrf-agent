package edu.whimc.overworld_agent.utils;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    private static final String PREFIX = "&8&l[&9&lAgent&8&l]&r ";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMMM d yyyy, h:mm a z");
    private static boolean debug = false;
    private static String debugPrefix = "[Agent] ";

    public static void setDebug(boolean shouldDebug) {
        debug = shouldDebug;
    }

    /**
     * Prints a debug message if "debug" is true.
     *
     * @param str Message to print
     */
    public static void debug(String str) {
        if (!debug) return;
        Bukkit.getLogger().info(color(debugPrefix + str));
    }

    /**
     * Sets the debug message prefix.
     *
     * @param prefix Prefix to be set
     */
    public static void setDebugPrefix(String prefix) {
        debugPrefix = "[" + prefix + "] ";
    }

    /**
     * Gets a nice formatted date.
     *
     * @param timestamp Timestamp of date to format
     * @return A formatted version of the given date
     */
    public static String getDate(Timestamp timestamp) {
        return DATE_FORMAT.format(new Date(timestamp.getTime()));
    }

    public static String getDateNow() {
        return getDate(new Timestamp(System.currentTimeMillis()));
    }

    public static Timestamp parseDate(String str) {
        try {
            return new Timestamp(DATE_FORMAT.parse(str).getTime());
        } catch (ParseException e) {
            return null;
        }
    }

    public static void msg(CommandSender sender, String... messages) {
        for (int ind = 0; ind < messages.length; ind++) {
            if (ind == 0) {
                sender.sendMessage(color(PREFIX + messages[ind]));
            } else {
                sender.sendMessage(color(messages[ind]));
            }
        }
    }

    public static String locationString(Location loc, boolean yawPitch) {
        NumberFormat formatter = new DecimalFormat("#0.00");

        StringBuilder message = new StringBuilder();
        message.append("&7World: &f&o" + loc.getWorld().getName());
        message.append("  &7X: &f&o" + formatter.format(loc.getX()));
        message.append("  &7Y: &f&o" + formatter.format(loc.getY()));
        message.append("  &7Z: &f&o" + formatter.format(loc.getZ()));

        if (yawPitch) {
            message.append("\n" + "    &7Pitch: &f&o" + formatter.format(loc.getPitch()));
            message.append("  &7Yaw: &f&o" + formatter.format(loc.getYaw()));
        }

        return message.toString();
    }


    public static String coloredSubstring(String str, int length) {
        str = color(str);
        StringBuilder newStr = new StringBuilder();
        int count = 0;
        boolean ignore = false;
        for (char chr : str.toCharArray()) {
            if (count >= length) break;
            newStr.append(chr);

            if (ignore) {
                ignore = false;
                continue;
            }

            if (chr == ChatColor.COLOR_CHAR) ignore = true;
            if (chr != ChatColor.COLOR_CHAR && !ignore) count++;
        }

        return newStr.toString().replace(ChatColor.COLOR_CHAR, '&');
    }

    public static void msgNoPrefix(CommandSender sender, Object... messages) {
        for (Object str : messages) {
            if (str instanceof BaseComponent) {
                sender.spigot().sendMessage((BaseComponent) str);
            } else {
                sender.sendMessage(color(str.toString()));
            }
        }
    }

    public static String color(String str) {
        if (str == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public static Integer parseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseIntWithError(CommandSender sender, String str) {
        Integer id = parseInt(str);
        if (id == null) {
            Utils.msg(sender, "&c\"&4" + str + "&c\" is an invalid number!");
            return null;
        }

        return id;
    }

    public static List<String> getWorldsTabComplete(String hint) {
        return Bukkit.getWorlds().stream()
                .filter(v -> v.getName().toLowerCase().startsWith(hint))
                .map(World::getName)
                .sorted()
                .collect(Collectors.toList());
    }

}