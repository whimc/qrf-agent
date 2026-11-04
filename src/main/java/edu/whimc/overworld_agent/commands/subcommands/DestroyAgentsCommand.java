package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.utils.AgentPermissions;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Destroys agents with LuckPerms-scoped targets: own, other players, or all.
 */
public class DestroyAgentsCommand extends AbstractSubCommand {

    private static final String ALL = "all";

    public DestroyAgentsCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand, PermissionDefault.FALSE);
        super.description("Remove an agent from the server (self, another player, or all)");
        super.arguments("[playerName|all]");
    }

    @Override
    protected boolean requiresSubcommandPermission() {
        return false;
    }

    @Override
    public boolean executeSubCommand(CommandSender sender, String[] args) {
        if (!AgentPermissions.canUseDestroy(sender)) {
            AgentPermissions.deny(sender, AgentPermissions.DESTROY_SELF);
            return true;
        }
        return super.executeSubCommand(sender, args);
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            if (sender instanceof Player player) {
                return destroyOne(sender, player.getName());
            }
            sender.sendMessage("Usage: /agent destroy <playerName|all>");
            return true;
        }
        return destroyOne(sender, args[0]);
    }

    private boolean destroyOne(CommandSender sender, String targetName) {
        if (targetName.equalsIgnoreCase(ALL)) {
            if (!AgentPermissions.canDestroyAll(sender)) {
                AgentPermissions.deny(sender, AgentPermissions.DESTROY_ALL);
                return true;
            }
            if (plugin.getAgents().isEmpty() && !anyOwnedAgentNpcExists()) {
                sender.sendMessage("There are no agents to destroy.");
                return true;
            }
            plugin.destroyAllOwnedAgents();
            sender.sendMessage("All agents were destroyed.");
            return true;
        }

        if (sender instanceof Player player && targetName.equalsIgnoreCase(player.getName())) {
            if (!AgentPermissions.canDestroySelf(sender)) {
                AgentPermissions.deny(sender, AgentPermissions.DESTROY_SELF);
                return true;
            }
        } else if (!AgentPermissions.canDestroyOther(sender)) {
            AgentPermissions.deny(sender, AgentPermissions.DESTROY_OTHER);
            return true;
        }

        if (!plugin.getAgents().containsKey(targetName) && !ownedAgentExists(targetName)) {
            sender.sendMessage("Player " + targetName + " does not have an agent.");
            return true;
        }

        String agentLabel = plugin.getAgents().containsKey(targetName)
                ? plugin.getAgents().get(targetName).getName()
                : targetName + "'s agent";
        plugin.destroyOwnedAgent(targetName);
        sender.sendMessage(agentLabel + " was destroyed.");
        return true;
    }

    private static boolean ownedAgentExists(String playerName) {
        for (NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
            String owner = OverworldAgent.resolveNpcOwnerName(npc);
            if (owner != null && owner.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyOwnedAgentNpcExists() {
        for (NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
            if (OverworldAgent.resolveNpcOwnerName(npc) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !AgentPermissions.canUseDestroy(sender)) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (AgentPermissions.canDestroySelf(sender) && sender instanceof Player player) {
            options.add(player.getName());
        }
        if (AgentPermissions.canDestroyOther(sender)) {
            for (String ownerName : plugin.getAgents().keySet()) {
                if (sender instanceof Player self && ownerName.equalsIgnoreCase(self.getName())) {
                    continue;
                }
                options.add(ownerName);
            }
        }
        if (AgentPermissions.canDestroyAll(sender) && ALL.startsWith(prefix)) {
            options.add(ALL);
        }

        return options.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
