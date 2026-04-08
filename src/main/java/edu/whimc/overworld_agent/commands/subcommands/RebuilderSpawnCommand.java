package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.traits.AgentFollowTuning;
import edu.whimc.overworld_agent.traits.RebuilderTrait;
import edu.whimc.overworld_agent.traits.SpawnExpertTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Class to define command for spawning an expert agent
 * @author sam
 */
public class RebuilderSpawnCommand extends AbstractSubCommand {


    private final String COMMAND = "builder";
    private final String SKIN = "astronaut";

    public RebuilderSpawnCommand(OverworldAgent plugin, String baseCommand, String subCommand){
        super(plugin, baseCommand, subCommand);
        super.description("Spawns an agent to follow sender with specified skin and name");
        super.arguments("");
    }
    /**
     * Creates a new expert agent and adds the entity to the world with the appropriate traits
     * @param sender - Source of the command
     * @param args - Passed command arguments
     * @return if the command was successfully executed
     */
    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        Player player;
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return true;
        } else {
            player = (Player) sender;
        }
        String playerName = player.getName();
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if(!plugin.getAgents().containsKey(player.getName())) {
            //NPC is a player and follows the assigned player and has behaviors specified in SpawnExpertTrait
            NPC npc = registry.createNPC(EntityType.PLAYER, "Builder");
            npc.getOrAddTrait(FollowTrait.class);
            npc.getOrAddTrait(Equipment.class);
            npc.getOrAddTrait(LookClose.class).setDisableWhileNavigating(true);
            AgentFollowTuning.applyForPlannedType(plugin, npc, EntityType.PLAYER);
            String path = "skins."+plugin.getSkinType();
            //Set NPC skin by grabbing values from config
            String signature = plugin.getConfig().getString(path + "." + SKIN + ".signature");
            String data = plugin.getConfig().getString(path + "." + SKIN + ".data");
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent(SKIN, signature, data);
            RebuilderTrait trait = new RebuilderTrait(playerName);
            npc.addTrait(trait);
            plugin.getQueryer().storeNewAgent(player, COMMAND, "Builder", "Builder", id -> {
                npc.spawn(player.getLocation());
                AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
                plugin.getAgents().put(player.getName(), npc);
            });
        } else {
            player.sendMessage("You already have an AI friend. You can change their name or skin by right clicking on them.");
        }
        return true;
    }

    /**
     * Allows tab completion of command
     * @param sender - Source of the command
     * @param args - Passed command arguments
     * @return list of tab completions (currently empty)
     */
    @Override
    protected List<java.lang.String> onTabComplete(CommandSender sender, java.lang.String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return list;
        }
        return Arrays.asList();
    }
}