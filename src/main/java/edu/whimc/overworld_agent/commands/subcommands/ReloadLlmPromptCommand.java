package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.LlmConfigAnnouncer;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.WorldLlmPrompt;
import edu.whimc.overworld_agent.dialoguetemplate.models.llm.WorldLlmPromptRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reloads per-world LLM prompts from {@code world-prompts/*.yml} without restarting the server.
 */
public class ReloadLlmPromptCommand extends AbstractSubCommand {

    public ReloadLlmPromptCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand);
        super.description("Reload per-world LLM prompts from world-prompts/");
        super.arguments("[world]");
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        WorldLlmPromptRegistry registry = plugin.getWorldLlmPromptRegistry();
        int loaded;
        try {
            loaded = registry.reload(plugin);
        } catch (Exception ex) {
            sender.sendMessage("Failed to reload world LLM prompts: " + ex.getMessage());
            plugin.getLogger().warning("world-prompts reload failed: " + ex.getMessage());
            return true;
        }

        sender.sendMessage("Reloaded " + loaded + " prompt file(s), "
                + registry.allWorldPrompts().size() + " world binding(s) from "
                + WorldLlmPromptRegistry.promptsDirectory(plugin));

        LlmConfigAnnouncer.announce(plugin);

        if (args.length >= 1) {
            String worldName = args[0];
            sender.sendMessage(registry.describeBuiltPrompt(plugin, worldName));
            return true;
        }

        if (sender instanceof Player player) {
            sender.sendMessage(registry.describeBuiltPrompt(plugin, player.getWorld().getName()));
        }

        for (Map.Entry<String, List<String>> entry : registry.worldNamesBySourceFile().entrySet()) {
            WorldLlmPrompt sample = registry.getForWorld(entry.getValue().get(0)).orElse(null);
            if (sample == null) {
                continue;
            }
            String rag = sample.hasRagDirectory() ? ", RAG: " + sample.getRagDirectory() : "";
            sender.sendMessage("  " + entry.getKey() + " -> " + String.join(", ", entry.getValue())
                    + " (" + sample.getPrompt().length() + " chars" + rag + ")");
        }
        registry.getDefaultPrompt().ifPresent(wp ->
                sender.sendMessage("  default <- " + wp.getSourceFile()
                        + " (" + wp.getPrompt().length() + " chars)"));

        return true;
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        List<String> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            worlds.add(world.getName());
        }
        for (WorldLlmPrompt wp : plugin.getWorldLlmPromptRegistry().allWorldPrompts()) {
            worlds.add(wp.getWorldName());
        }
        return worlds.stream()
                .distinct()
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }
}
