package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.utils.AgentEntityTypes;
import edu.whimc.overworld_agent.utils.CitizensSkinUrls;
import edu.whimc.overworld_agent.traits.AgentFollowCatchUpTrait;
import edu.whimc.overworld_agent.traits.AgentFollowTuning;
import edu.whimc.overworld_agent.traits.AgentPermanentFlyingTrait;
import edu.whimc.overworld_agent.traits.SpawnExpertTrait;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
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
 * Spawns a guide agent for the executing player.
 */
public class ExpertSpawnCommand extends AbstractSubCommand {

    private static final String COMMAND = "expert";
    private static final List<String> ANIMAL_ENTITY_NAMES = AgentEntityTypes.animalNamesLowercaseSorted();

    public ExpertSpawnCommand(OverworldAgent plugin, String baseCommand, String subCommand) {
        super(plugin, baseCommand, subCommand);
        super.description("Spawns an agent to follow sender with specified entity type/skin and name");
        super.arguments("[entityType] [skinName | --url <https://...>] agentName");
    }

    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("You must be a player");
            return true;
        }

        if (plugin.getAgents().containsKey(player.getName())) {
            player.sendMessage("You already have an AI friend. You can change their name by right clicking on them.");
            return true;
        }

        SpawnRequest request = parseSpawnRequest(player, args);
        if (request == null) {
            return true;
        }

        NPC npc = createGuideNpc(player, request.entityType(), request.npcName());
        if (npc == null) {
            return true;
        }

        if (request.skinUrl() != null) {
            if (!plugin.getConfig().getBoolean("agent-spawn.allow-url-skins", true)) {
                npc.destroy();
                player.sendMessage("Custom URL skins are disabled on this server.");
                return true;
            }

            player.sendMessage("Fetching skin from URL...");
            CitizensSkinUrls.fetchFromUrl(
                    plugin,
                    request.skinUrl(),
                    request.slim(),
                    skinData -> applySkinAndFinishSpawn(player, npc, request.npcName(), request.entityType(), skinData),
                    error -> {
                        npc.destroy();
                        player.sendMessage(
                                "Could not load skin from that URL. Use a direct https:// link to a .png file. "
                                        + "(" + error + ")"
                        );
                    }
            );
            return true;
        }

        if (request.entityType() == EntityType.PLAYER) {
            String path = "skins." + plugin.getSkinType();
            String signature = plugin.getConfig().getString(path + "." + request.configSkinName() + ".signature");
            String data = plugin.getConfig().getString(path + "." + request.configSkinName() + ".data");
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent(request.configSkinName(), signature, data);
        }

        finishSpawn(player, npc, request.npcName(), request.entityType(), request.configSkinName());
        return true;
    }

    private SpawnRequest parseSpawnRequest(Player player, String[] args) {
        int urlIndex = indexOfFlag(args, "--url");
        if (urlIndex >= 0) {
            return parseUrlSpawnRequest(player, args, urlIndex);
        }
        return parseConfigSkinSpawnRequest(player, args);
    }

    private SpawnRequest parseUrlSpawnRequest(Player player, String[] args, int urlIndex) {
        if (urlIndex + 1 >= args.length) {
            player.sendMessage("Provide a URL after --url (direct https link to a .png skin image).");
            return null;
        }

        String skinUrl = args[urlIndex + 1];
        if (!CitizensSkinUrls.isHttpsUrl(skinUrl)) {
            player.sendMessage("Skin URL must start with https:// and point to a direct image link.");
            return null;
        }

        boolean slim = indexOfFlag(args, "--slim") >= 0;
        EntityType entityType = EntityType.PLAYER;

        if (urlIndex > 0) {
            String first = args[0];
            if (!first.startsWith("--")) {
                EntityType parsed = parseEntityTypeOrNull(first);
                if (parsed != null) {
                    entityType = parsed;
                } else if (!first.equalsIgnoreCase("player")) {
                    player.sendMessage("For custom URL skins use: /agent spawn player --url <https://...> <name>");
                    return null;
                }
            }
        }

        if (entityType != EntityType.PLAYER) {
            player.sendMessage("Custom URL skins are only supported for player agents.");
            return null;
        }

        String npcName = joinTokensSkippingFlags(args, urlIndex + 2);
        if (npcName.isBlank()) {
            player.sendMessage("Provide a name for your agent after the skin URL.");
            return null;
        }

        return new SpawnRequest(entityType, null, skinUrl, slim, truncateNpcName(npcName));
    }

    private SpawnRequest parseConfigSkinSpawnRequest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(
                    "Give your AI friend an appearance and a name. "
                            + "Example: /agent spawn player wmscientist Alex "
                            + "or /agent spawn player --url https://example.com/skin.png Alex"
            );
            return null;
        }

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
                    return null;
                }
                skinName = args[1];
                nameStartIndex = 2;
            } else {
                skinName = args[0];
                nameStartIndex = 1;
            }
        } else {
            nameStartIndex = 1;
        }

        String npcName = joinTokens(args, nameStartIndex);
        if (npcName.isBlank()) {
            player.sendMessage("Provide a name for your agent.");
            return null;
        }
        npcName = truncateNpcName(npcName);

        if (entityType == EntityType.PLAYER) {
            String path = "skins." + plugin.getSkinType();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            Set<String> keys = sec != null ? sec.getKeys(false) : Set.of();
            if (skinName == null || !keys.contains(skinName)) {
                player.sendMessage(
                        "Use a valid skin name from tab completion, or spawn with "
                                + "/agent spawn player --url <https://...> <name> for a custom skin."
                );
                return null;
            }
        }

        return new SpawnRequest(entityType, skinName, null, false, npcName);
    }

    private NPC createGuideNpc(Player player, EntityType entityType, String npcName) {
        if (entityType != EntityType.PLAYER) {
            if (!AgentEntityTypes.isAllowedNonPlayerAgent(entityType)) {
                player.sendMessage("That entity type cannot be used as an animal agent.");
                return null;
            }
            Class<?> entityClass = entityType.getEntityClass();
            if (entityClass == null || !entityType.isAlive()) {
                player.sendMessage("That entity type cannot be used as an animal agent.");
                return null;
            }
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(entityType, npcName);
        npc.getOrAddTrait(LookClose.class).setDisableWhileNavigating(true);
        AgentFollowTuning.applyForPlannedType(plugin, npc, entityType);
        SpawnExpertTrait trait = new SpawnExpertTrait();
        trait.setPlayer(player);
        trait.setInputType(true);
        npc.addTrait(trait);
        npc.addTrait(new AgentPermanentFlyingTrait());
        npc.addTrait(new AgentFollowCatchUpTrait());
        return npc;
    }

    private void applySkinAndFinishSpawn(
            Player player,
            NPC npc,
            String npcName,
            EntityType entityType,
            CitizensSkinUrls.SkinData skinData
    ) {
        if (plugin.getAgents().containsKey(player.getName())) {
            npc.destroy();
            player.sendMessage("You already have an AI friend.");
            return;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinPersistent(skinData.cacheId(), skinData.signature(), skinData.texture());
        finishSpawn(player, npc, npcName, entityType, skinData.cacheId());
    }

    private void finishSpawn(Player player, NPC npc, String npcName, EntityType entityType, String agentSkinOrType) {
        String storedAppearance = entityType == EntityType.PLAYER ? agentSkinOrType : entityType.name();
        plugin.getQueryer().storeNewAgent(player, COMMAND, npcName, storedAppearance, id -> {
            npc.spawn(player.getLocation());
            AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, player);
            plugin.getAgents().put(player.getName(), npc);
        });
    }

    private static String truncateNpcName(String npcName) {
        if (npcName.length() > 25) {
            return npcName.substring(0, 25);
        }
        return npcName;
    }

    private static String joinTokens(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private static String joinTokensSkippingFlags(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private static int indexOfFlag(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static EntityType parseEntityTypeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    protected List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> entityOpts = new ArrayList<>();
            entityOpts.add("player");
            entityOpts.addAll(ANIMAL_ENTITY_NAMES);
            return entityOpts.stream()
                    .filter(v -> v.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            if ("--url".startsWith(prefix)) {
                options.add("--url");
            }

            String path = "skins." + plugin.getSkinType();
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
            if (sec != null) {
                options.addAll(
                        sec.getKeys(false).stream()
                                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix))
                                .collect(Collectors.toList())
                );
            }
            return options;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("--url")) {
            return List.of();
        }

        return List.of();
    }

    private record SpawnRequest(
            EntityType entityType,
            String configSkinName,
            String skinUrl,
            boolean slim,
            String npcName
    ) {
    }
}
