package edu.whimc.overworld_agent.commands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.subcommands.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified {@code /agent} command ({@code /agents} is a Bukkit alias). Subcommands use permission
 * nodes {@code whimc-agent.agent.<subcommand>}.
 */
public class AgentCommand implements CommandExecutor, TabCompleter {

    private final OverworldAgent plugin;
    private final Map<String, AbstractSubCommand> subCommands = new HashMap<>();

    public AgentCommand(OverworldAgent plugin) {
        this.plugin = plugin;
        String base = "agent";
        subCommands.put("chat", new ChatCommand(plugin, base, "chat"));
        subCommands.put("spawn", plugin.getExpertSpawnCommand());
        subCommands.put("despawn", new DespawnAgentsCommand(plugin, base, "despawn"));
        subCommands.put("destroy", new DestroyAgentsCommand(plugin, base, "destroy"));
        subCommands.put("rebuilderspawn", new RebuilderSpawnCommand(plugin, base, "rebuilderspawn"));
        subCommands.put("reactivate", new SpawnAgentsCommand(plugin, base, "reactivate"));
        subCommands.put("skin_type", new SkinTypeCommand(plugin, base, "skin_type"));
        subCommands.put("about", new AboutAgentsCommand(plugin, base, "about"));
        subCommands.put("reload_llm_prompt", new ReloadLlmPromptCommand(plugin, base, "reload_llm_prompt"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                return subCommands.get("chat").executeSubCommand(sender, new String[0]);
            }
            sender.sendMessage("Usage: /" + commandLabel + " <subcommand> — try /" + commandLabel + " about");
            return true;
        }

        AbstractSubCommand subCmd = subCommands.getOrDefault(args[0].toLowerCase(), null);
        if (subCmd == null) {
            sender.sendMessage("Unknown subcommand. Use /" + commandLabel + " about");
            return true;
        }

        return subCmd.executeSubCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return subCommands.keySet().stream()
                    .filter(v -> !v.equals("rebuilderspawn") || plugin.isBuilderEnabled())
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 1) {
            return subCommands.keySet()
                    .stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase()))
                    .filter(v -> !v.equals("rebuilderspawn") || plugin.isBuilderEnabled())
                    .sorted()
                    .collect(Collectors.toList());
        }

        AbstractSubCommand subCmd = subCommands.getOrDefault(args[0].toLowerCase(), null);
        if (subCmd == null) {
            return null;
        }

        return subCmd.executeOnTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
