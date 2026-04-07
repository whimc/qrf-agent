package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.traits.AgentFollowTuning;
import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import edu.whimc.overworld_agent.traits.SpawnExpertTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.FollowTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Class to define command for spawning an expert agent
 * @author sam
 */
public class SpeechSpawnCommand extends AbstractSubCommand {


    private final String COMMAND = "speech";

    public SpeechSpawnCommand(OverworldAgent plugin, String baseCommand, String subCommand){
        super(plugin, baseCommand, subCommand);
        super.description("Spawns an agent to follow sender with specified skin and name");
        super.arguments("skinName agentName");
    }
    /**
     * Creates a new expert agent and adds the entity to the world with the appropriate traits
     * @param sender - Source of the command
     * @param args - Passed command arguments
     * @return if the command was successfully executed
     */
    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        //Skin name 1st, NPC 2nd

        String npcName = "";
        String playerName = "";
        Player player;


        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return true;
        } else {
            player = (Player) sender;
            playerName = player.getName();
            if(args.length < 2){
                player.sendMessage("Make sure to give your AI friend a skin and a name! Please try again");
                return true;
            }
        }
        String skinName = args[0];
        for(int k = 1; k < args.length; k++){
            npcName += args[k] + " ";
        }
        npcName = npcName.substring(0,npcName.length()-1);
        if(!plugin.getAgents().containsKey(playerName)) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("skins");
            Set<String> keys = sec.getKeys(false);
            List<String> skins = new ArrayList<>(keys);
            if (!skins.contains(skinName)) {
                player.sendMessage("Make sure to give your AI friend a valid skin name, you can press tab to complete one of the options! Please try again");
                return false;
            }
            NPCRegistry registry = CitizensAPI.getNPCRegistry();

            //NPC is a player and follows the assigned player and has behaviors specified in SpawnExpertTrait
            NPC npc = registry.createNPC(EntityType.PLAYER, npcName);
            npc.getOrAddTrait(FollowTrait.class).follow(player);
            npc.getOrAddTrait(LookClose.class).setDisableWhileNavigating(true);
            AgentFollowTuning.applyForPlannedType(plugin, npc, EntityType.PLAYER);
            SpawnExpertTrait trait = new SpawnExpertTrait();
            trait.setPlayer(player);
            trait.setInputType(false);
            npc.addTrait(trait);
            npc.addTrait(new AgentPermanentFlyingTrait());

            //Set NPC skin by grabbing values from config
            String signature = plugin.getConfig().getString("skins." + skinName + ".signature");
            String data = plugin.getConfig().getString("skins." + skinName + ".data");
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent(skinName, signature, data);
            plugin.getQueryer().storeNewAgent(player, COMMAND, npcName, skinName, id -> {
                npc.spawn(player.getLocation());
                plugin.getAgents().put(player.getName(), npc);
            });
            return true;
        }
        player.sendMessage("You already have an AI friend. You can change their name with /agent name and skin with /agent skin.");
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
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("skins");
            Set<String> keys = sec.getKeys(false);
            List<String> skins = new ArrayList<>(keys);
            return skins;
        }
        return Arrays.asList();
    }
}
