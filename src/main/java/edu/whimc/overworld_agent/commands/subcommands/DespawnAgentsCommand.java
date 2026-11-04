package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.utils.AgentPermissions;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Despawns agents with LuckPerms-scoped targets: own, other players, or all.
 */
public class DespawnAgentsCommand extends AbstractSubCommand {

    private static final String ALL = "all";

    public DespawnAgentsCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand, PermissionDefault.FALSE);
        super.description("Despawn an agent (self, another player, or all)");
        super.arguments("[playerName|all]");
    }

    @Override
    protected boolean requiresSubcommandPermission() {
        return false;
    }

    @Override
    public boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!AgentPermissions.canUseDespawn(sender)) {
            AgentPermissions.deny(sender, AgentPermissions.DESPAWN_SELF);
            return true;
        }
        return super.executeSubCommand(sender, args);
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            if (sender instanceof Player player) {
                return despawnOne(sender, player.getName());
            }
            sender.sendMessage("Usage: /agent despawn <playerName|all>");
            return true;
        }
        return despawnOne(sender, args[0]);
    }

    private boolean despawnOne(CommandSender sender, String targetName) {
        if (targetName.equalsIgnoreCase(ALL)) {
            if (!AgentPermissions.canDespawnAll(sender)) {
                AgentPermissions.deny(sender, AgentPermissions.DESPAWN_ALL);
                return true;
            }
            Map<String, NPC> npcs = plugin.getAgents();
            int count = 0;
            for (Map.Entry<String, NPC> entry : npcs.entrySet()) {
                NPC npc = entry.getValue();
                if (npc == null) {
                    continue;
                }
                if (Bukkit.getPlayer(entry.getKey()) != null) {
                    npc.despawn();
                    count++;
                }
            }
            sender.sendMessage("Despawned " + count + " agent(s). Run /agent spawn to bring them back.");
            return true;
        }

        if (sender instanceof Player player && targetName.equalsIgnoreCase(player.getName())) {
            if (!AgentPermissions.canDespawnSelf(sender)) {
                AgentPermissions.deny(sender, AgentPermissions.DESPAWN_SELF);
                return true;
            }
        } else if (!AgentPermissions.canDespawnOther(sender)) {
            AgentPermissions.deny(sender, AgentPermissions.DESPAWN_OTHER);
            return true;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        if (online == null) {
            sender.sendMessage("Player must be online to despawn their agent.");
            return true;
        }

        NPC npc = plugin.getAgents().get(online.getName());
        if (npc == null) {
            sender.sendMessage("Player does not have an agent.");
            return true;
        }

        npc.despawn();
        sender.sendMessage(npc.getName() + " was despawned. Run /agent spawn to bring them back (not /agent destroy).");
        return true;
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !AgentPermissions.canUseDespawn(sender)) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (AgentPermissions.canDespawnSelf(sender) && sender instanceof Player player) {
            options.add(player.getName());
        }
        if (AgentPermissions.canDespawnOther(sender)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player self && online.equals(self)) {
                    continue;
                }
                options.add(online.getName());
            }
        }
        if (AgentPermissions.canDespawnAll(sender) && ALL.startsWith(prefix)) {
            options.add(ALL);
        }

        return options.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
