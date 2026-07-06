package edu.whimc.overworld_agent.utils;

import edu.whimc.overworld_agent.OverworldAgent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * LuckPerms-friendly permission nodes for agent spawn, edit (dialogue menu), despawn, and destroy.
 * Register once on plugin enable via {@link #register()}.
 */
public final class AgentPermissions {

    public static final String SPAWN = node("spawn");
    public static final String SPAWN_PLAYER = node("spawn.player");
    public static final String SPAWN_ANIMAL = node("spawn.animal");

    public static final String DESPAWN = node("despawn");
    public static final String DESPAWN_SELF = node("despawn.self");
    public static final String DESPAWN_OTHER = node("despawn.other");
    public static final String DESPAWN_ALL = node("despawn.all");

    public static final String DESTROY = node("destroy");
    public static final String DESTROY_SELF = node("destroy.self");
    public static final String DESTROY_OTHER = node("destroy.other");
    public static final String DESTROY_ALL = node("destroy.all");

    public static final String EDIT = node("edit");
    public static final String EDIT_NAME = node("edit.name");
    public static final String EDIT_SKIN = node("edit.skin");
    public static final String EDIT_TYPE = node("edit.type");
    public static final String EDIT_TYPE_ANIMAL = node("edit.type.animal");

    private AgentPermissions() {}

    private static String node(String suffix) {
        return OverworldAgent.PERM_PREFIX + ".agent." + suffix;
    }

    public static void register() {
        Permission agentWildcard = ensurePermission(
                OverworldAgent.PERM_PREFIX + ".agent.*",
                "All /agent subcommand permissions",
                PermissionDefault.FALSE,
                OverworldAgent.PERM_PREFIX + ".*"
        );

        childOnly("spawn.player", "Spawn player-model agents", PermissionDefault.TRUE, SPAWN);
        childOnly("spawn.animal", "Spawn animal mob agents", PermissionDefault.FALSE, SPAWN);

        childOnly("despawn.self", "Despawn your own agent", PermissionDefault.FALSE, DESPAWN);
        childOnly("despawn.other", "Despawn another player's agent", PermissionDefault.FALSE, DESPAWN);
        childOnly("despawn.all", "Despawn every online player's agent", PermissionDefault.FALSE, DESPAWN);

        childOnly("destroy.self", "Destroy your own agent", PermissionDefault.FALSE, DESTROY);
        childOnly("destroy.other", "Destroy another player's agent", PermissionDefault.FALSE, DESTROY);
        childOnly("destroy.all", "Destroy every agent", PermissionDefault.FALSE, DESTROY);

        ensurePermission(EDIT, "Agent edit menu (right-click dialogue)", PermissionDefault.FALSE, agentWildcard.getName());
        childOnly("edit.name", "Change your agent's name", PermissionDefault.FALSE, EDIT);
        childOnly("edit.skin", "Change your agent's skin", PermissionDefault.FALSE, EDIT);
        childOnly("edit.type", "Change your agent's entity type", PermissionDefault.FALSE, EDIT);
        childOnly("edit.type.animal", "Change your agent to an animal mob type", PermissionDefault.FALSE, EDIT_TYPE);
    }

    private static void childOnly(String suffix, String description, PermissionDefault def, String parentName) {
        ensurePermission(node(suffix), description, def, parentName);
    }

    private static Permission ensurePermission(
            String name,
            String description,
            PermissionDefault def,
            String parentName
    ) {
        Permission existing = Bukkit.getPluginManager().getPermission(name);
        if (existing != null) {
            return existing;
        }
        Permission perm = new Permission(name, description, def);
        perm.addParent(parentName, true);
        Bukkit.getPluginManager().addPermission(perm);
        return perm;
    }

    /** Server operators bypass all granular LuckPerms checks for agent commands. */
    public static boolean bypassesRestrictions(CommandSender sender) {
        return sender != null && sender.isOp();
    }

    private static boolean has(CommandSender sender, String permission) {
        return bypassesRestrictions(sender) || sender.hasPermission(permission);
    }

    public static boolean canSpawnPlayer(CommandSender sender) {
        return has(sender, SPAWN_PLAYER);
    }

    public static boolean canSpawnAnimal(CommandSender sender) {
        return has(sender, SPAWN_ANIMAL);
    }

    public static boolean canSpawnEntityType(CommandSender sender, EntityType type) {
        if (type == null || type == EntityType.PLAYER) {
            return canSpawnPlayer(sender);
        }
        return canSpawnAnimal(sender);
    }

    public static boolean canDespawnSelf(CommandSender sender) {
        return has(sender, DESPAWN_SELF);
    }

    public static boolean canDespawnOther(CommandSender sender) {
        return has(sender, DESPAWN_OTHER);
    }

    public static boolean canDespawnAll(CommandSender sender) {
        return has(sender, DESPAWN_ALL);
    }

    public static boolean canUseDespawn(CommandSender sender) {
        return canDespawnSelf(sender) || canDespawnOther(sender) || canDespawnAll(sender);
    }

    public static boolean canDestroySelf(CommandSender sender) {
        return has(sender, DESTROY_SELF);
    }

    public static boolean canDestroyOther(CommandSender sender) {
        return has(sender, DESTROY_OTHER);
    }

    public static boolean canDestroyAll(CommandSender sender) {
        return has(sender, DESTROY_ALL);
    }

    public static boolean canUseDestroy(CommandSender sender) {
        return canDestroySelf(sender) || canDestroyOther(sender) || canDestroyAll(sender);
    }

    public static boolean canEditName(Player player) {
        return has(player, EDIT_NAME);
    }

    public static boolean canEditSkin(Player player) {
        return has(player, EDIT_SKIN);
    }

    public static boolean canEditTypeMenu(Player player) {
        return has(player, EDIT_TYPE);
    }

    public static boolean canEditEntityType(Player player, EntityType targetType) {
        if (!canEditTypeMenu(player)) {
            return false;
        }
        if (targetType != null && targetType != EntityType.PLAYER) {
            return has(player, EDIT_TYPE_ANIMAL);
        }
        return true;
    }

    public static boolean canOpenEditMenu(Player player) {
        return bypassesRestrictions(player)
                || canEditName(player)
                || canEditSkin(player)
                || canEditTypeMenu(player);
    }

    public static boolean canManageAgent(CommandSender sender, String ownerName, boolean allTargets) {
        if (allTargets) {
            return false;
        }
        if (sender instanceof Player player && player.getName().equalsIgnoreCase(ownerName)) {
            return true;
        }
        return false;
    }

    public static String ownerName(NPC npc) {
        if (npc == null || !npc.hasTrait(edu.whimc.overworld_agent.traits.SpawnExpertTrait.class)) {
            return null;
        }
        return npc.getOrAddTrait(edu.whimc.overworld_agent.traits.SpawnExpertTrait.class).getAssignedPlayerName();
    }

    public static void deny(CommandSender sender, String permission) {
        Utils.msg(sender,
                "&cYou do not have the required permission!",
                "  &f&o" + permission);
    }
}
