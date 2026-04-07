package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.utils.AgentEntityTypes;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to define command for spawning an expert agent
 * @author sam
 */
public class ExpertSpawnCommand extends AbstractSubCommand {


    private final String COMMAND = "expert";
    private static final List<String> ANIMAL_ENTITY_NAMES = AgentEntityTypes.animalNamesLowercaseSorted();

    public ExpertSpawnCommand(OverworldAgent plugin, String baseCommand, String subCommand){
        super(plugin, baseCommand, subCommand);
        super.description("Spawns an agent to follow sender with specified entity type/skin and name");
        super.arguments("[entityType] skinName agentName");
    }
    /**
     * Creates a new expert agent and adds the entity to the world with the appropriate traits
     * @param sender - Source of the command
     * @param args - Passed command arguments
     * @return if the command was successfully executed
     */
    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
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
                player.sendMessage("Make sure to give your AI friend an appearance and a name! Please try again");
                return true;
            }
        }

        // New (preferred) syntax:
        // /agents spawn <entityType> [skinName] <agentName...>
        // Backwards compatible syntax:
        // /agents spawn <skinName> <agentName...>  (entityType defaults to PLAYER)
        EntityType entityType = parseEntityTypeOrNull(args[0]);
        boolean explicitEntityType = entityType != null;
        if (!explicitEntityType) {
            entityType = EntityType.PLAYER;
        }

        String skinName = null;
        int nameStartIndex;
        if (entityType == EntityType.PLAYER) {
            if (explicitEntityType) {
                if (args.length < 3) {
                    player.sendMessage("For player agents, provide an entity type, a skin, and a name.");
                    return true;
                }
                skinName = args[1];
                nameStartIndex = 2;
            } else {
                skinName = args[0];
                nameStartIndex = 1;
            }
        } else {
            // For non-player entity types, skin is not used.
            nameStartIndex = 1;
        }

        for(int k = nameStartIndex; k < args.length; k++){
            npcName += args[k] + " ";
        }
        npcName = npcName.substring(0,npcName.length()-1);
        if(npcName.length() > 25){
            npcName = npcName.substring(0,25);
        }
        if(!plugin.getAgents().containsKey(playerName)) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            Set<String> keys = sec != null ? sec.getKeys(false) : Set.of();
            List<String> skins = new ArrayList<>(keys);
            if (entityType == EntityType.PLAYER && (skinName == null || !skins.contains(skinName))) {
                player.sendMessage("Make sure to give your AI friend a valid skin name, you can press tab to complete one of the options! Please try again");
                return true;
            }
            NPCRegistry registry = CitizensAPI.getNPCRegistry();

            // Validate entity type if non-player (fixed whitelist in AgentEntityTypes; not all Animals in the game)
            if (entityType != EntityType.PLAYER) {
                if (!AgentEntityTypes.isAllowedNonPlayerAgent(entityType)) {
                    player.sendMessage("That entity type cannot be used as an animal agent.");
                    return true;
                }
                Class<?> entityClass = entityType.getEntityClass();
                if (entityClass == null || !entityType.isAlive()) {
                    player.sendMessage("That entity type cannot be used as an animal agent.");
                    return true;
                }
            }

            NPC npc = registry.createNPC(entityType, npcName);
            npc.getOrAddTrait(FollowTrait.class).follow(player);
            npc.getOrAddTrait(LookClose.class).setDisableWhileNavigating(true);
            AgentFollowTuning.applyForPlannedType(plugin, npc, entityType);
            SpawnExpertTrait trait = new SpawnExpertTrait();
            trait.setPlayer(player);
            trait.setInputType(true);
            npc.addTrait(trait);
            npc.addTrait(new AgentPermanentFlyingTrait());

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
                npc.getOrAddTrait(FollowTrait.class).follow(player);
                plugin.getAgents().put(player.getName(), npc);
            });
            return true;
        }
        player.sendMessage("You already have an AI friend. You can change their name by right clicking on them.");
        return true;
    }

    private static EntityType parseEntityTypeOrNull(String raw) {
        if (raw == null) return null;
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
            // First token is always entity type: `player` or an animal enum name (skins are the *second* token after `player`).
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> entityOpts = new ArrayList<>();
            entityOpts.add("player");
            entityOpts.addAll(ANIMAL_ENTITY_NAMES);
            return entityOpts.stream()
                    .filter(v -> v.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            String path = "skins";
            String type = plugin.getSkinType();
            path = path + "." + type;
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            if (sec == null) {
                return Arrays.asList();
            }
            Set<String> keys = sec.getKeys(false);
            List<String> skins = new ArrayList<>(keys);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return skins.stream()
                    .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Arrays.asList();
    }
}
