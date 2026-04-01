package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Class to define command for spawning an expert agent
 * @author sam
 */
public class ExpertSpawnCommand extends AbstractSubCommand {


    private final String COMMAND = "expert";

    public ExpertSpawnCommand(OverworldAgent plugin, String baseCommand, String subCommand){
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

        String path = "skins."+plugin.getSkinType();

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

        EntityType entityType = EntityType.PLAYER;
        int nameEndExclusive = args.length;
        if (args.length >= 3) {
            EntityType parsed = parseSupportedEntityType(args[args.length - 1]);
            if (parsed != null) {
                entityType = parsed;
                nameEndExclusive = args.length - 1;
            }
        }

        for(int k = 1; k < nameEndExclusive; k++){
            npcName += args[k] + " ";
        }
        npcName = npcName.substring(0,npcName.length()-1);
        if(npcName.length() > 25){
            npcName = npcName.substring(0,25);
        }
        if(!plugin.getAgents().containsKey(playerName)) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            Set<String> keys = sec.getKeys(false);
            List<String> skins = new ArrayList<>(keys);
            if (entityType == EntityType.PLAYER && !skins.contains(skinName)) {
                player.sendMessage("Make sure to give your AI friend a valid skin name, you can press tab to complete one of the options! Please try again");
                return false;
            }
            NPCRegistry registry = CitizensAPI.getNPCRegistry();

            //NPC is a player and follows the assigned player and has behaviors specified in SpawnExpertTrait
            NPC npc = registry.createNPC(entityType, npcName);
            npc.getOrAddTrait(FollowTrait.class).follow(player);
            npc.getOrAddTrait(LookClose.class).setDisableWhileNavigating(true);
            npc.getNavigator().getLocalParameters().range(15);
            SpawnExpertTrait trait = new SpawnExpertTrait();
            trait.setPlayer(player);
            trait.setInputType(true);
            npc.addTrait(trait);

            if (entityType == EntityType.PLAYER) {
                //Set NPC skin by grabbing values from config
                String signature = plugin.getConfig().getString(path + "." + skinName + ".signature");
                String data = plugin.getConfig().getString(path + "." + skinName + ".data");
                SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                skinTrait.setSkinPersistent(skinName, signature, data);
            }

            String agentSkinOrType = entityType == EntityType.PLAYER ? skinName : entityType.name();
            plugin.getQueryer().storeNewAgent(player, COMMAND, npcName, agentSkinOrType, id -> {
                npc.spawn(player.getLocation());
                plugin.getAgents().put(player.getName(), npc);
            });
            return true;
        }
        player.sendMessage("You already have an AI friend. You can change their name by right clicking on them.");
        return true;
    }

    private static EntityType parseSupportedEntityType(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "PLAYER" -> EntityType.PLAYER;
            case "SHEEP" -> EntityType.SHEEP;
            case "AXOLOTL" -> EntityType.AXOLOTL;
            default -> null;
        };
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
            String path = "skins";
            String type = plugin.getSkinType();
            path = path + "." + type;
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            Set<String> keys = sec.getKeys(false);
            List<String> skins = new ArrayList<>(keys);
            return skins;
        }
        return Arrays.asList();
    }
}
