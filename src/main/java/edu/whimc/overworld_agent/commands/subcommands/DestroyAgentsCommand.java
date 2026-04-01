package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Class to define command for despawning agents
 * @author sam
 */
public class DestroyAgentsCommand extends AbstractSubCommand {
    private static final String ALL = "all";

    public DestroyAgentsCommand(OverworldAgent plugin, String baseCommand, String subCommand){
        super(plugin, baseCommand, subCommand);
        super.description("Removes specified player's agent from the serve (all for everyones')");
        super.arguments("playerName");
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {

        Map<String, NPC> npcs = plugin.getAgents();
        if (args.length < 1) {
            sender.sendMessage("No player name was given");
            return true;
        }
        String playerName = args[0];
        if (playerName.equalsIgnoreCase(ALL)) {
            if (npcs.isEmpty()) {
                sender.sendMessage("There are no agents to destroy.");
                return true;
            }

            for (NPC npc : new ArrayList<>(npcs.values())) {
                if (npc == null) continue;

                if (npc.isSpawned()) {
                    npc.despawn();
                }
                npc.destroy();
            }

            plugin.removeAgents();
            sender.sendMessage("All agents were destroyed.");
            return true;
        }

        // Single player destroy - we do NOT require the player to be online
        NPC npc = npcs.get(playerName);

        if (npc == null) {
            sender.sendMessage("Player " + playerName + " does not have an agent.");
            return true;
        }

        if (npc.isSpawned()) {
            npc.despawn();
        }

        npc.destroy();
        plugin.removeAgent(playerName);

        sender.sendMessage(npc.getName() + " was destroyed.");
        return true;
    }

    @Override
    protected List<java.lang.String> onTabComplete(CommandSender sender, java.lang.String[] args) {
        List<String> list = new ArrayList<String>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            list.add(p.getName());
        }
        list.add("all");
        return list;
    }
}