package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/**
 * Shows plugin name and version from {@code plugin.yml}.
 */
public class AboutAgentsCommand extends AbstractSubCommand {

    public AboutAgentsCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand, PermissionDefault.TRUE);
        super.description("Show this plugin version");
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        PluginMeta meta = plugin.getPluginMeta();
        String desc = meta.getDescription() != null ? meta.getDescription() : "";
        sender.sendMessage(Component.text()
                .append(Component.text(meta.getName(), NamedTextColor.AQUA))
                .append(Component.text(" v", NamedTextColor.GRAY))
                .append(Component.text(meta.getVersion(), NamedTextColor.WHITE)));
        if (!desc.isBlank()) {
            sender.sendMessage(Component.text(desc, NamedTextColor.DARK_GRAY));
        }
        return true;
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
