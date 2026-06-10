package edu.whimc.overworld_agent.traits;

import edu.whimc.overworld_agent.OverworldAgent;

import edu.whimc.overworld_agent.dialoguetemplate.BuilderDialogue;
import edu.whimc.overworld_agent.dialoguetemplate.Dialogue;
import net.citizensnpcs.Settings;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.trait.FollowTrait;
import org.bukkit.Bukkit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class to define Novice Trait has agent able to guide player to a location on right click,
 * waits if player gets too far behind when following to its destination, and prompts player for an observation at the end of its path
 * @author sam
 */
public class RebuilderTrait extends Trait {
    private OverworldAgent plugin;
    @Persist private String target;
    /**
     * Constructor sets name of trait and instantiates plugin
     */
    public RebuilderTrait(String playerName) {
        super("rebuilder");
        plugin = JavaPlugin.getPlugin(OverworldAgent.class);
        target = playerName;
    }

    public String getTargetPlayerName() {
        return target;
    }
    // Here you should load up any values you have previously saved (optional).
    // This does NOT get called when applying the trait for the first time, only loading onto an existing npc at server start.
    // This is called AFTER onAttach so you can load defaults in onAttach and they will be overridden here.
    // This is called BEFORE onSpawn, npc.getEntity() will return null.
    public void load(DataKey key) {
        target = key.getString("target", target);
    }

    // Save settings for this NPC (optional). These values will be persisted to the Citizens saves file
    public void save(DataKey key) {
        key.setString("target",target);
    }

    /**
     * Event handler when the agent is right clicked on and prompts the user for an observation
     * @param event the right click event
     */
    @EventHandler
    public void click(net.citizensnpcs.api.event.NPCRightClickEvent event) {
        //Handle a click on a NPC. The event has a getNPC() method.
        //Be sure to check event.getNPC() == this.getNPC() so you only handle clicks on this NPC!
        Player sender = event.getClicker();
        if(sender == Bukkit.getPlayer(target)) {
            if (event.getNPC() == this.getNPC()) {
                if (plugin.getInProgressTemplates().containsKey(sender)) {
                    BuilderDialogue bd = plugin.getInProgressTemplates().get(sender);
                    bd.doDialogue();
                } else {
                    BuilderDialogue bd = new BuilderDialogue(plugin, sender, true);
                    bd.doDialogue();
                }
            }
        }
    }

    // Called every tick
    @Override
    public void run() {
        if(npc.isSpawned() && target != null && Bukkit.getPlayer(target) != null){
            if (!npc.getEntity().getWorld().equals(Bukkit.getPlayer(target).getWorld())) {
                if (Settings.Setting.FOLLOW_ACROSS_WORLDS.asBoolean()) {
                    Player follower = Bukkit.getPlayer(target);
                    npc.despawn();
                    npc.spawn(AgentFollowCatchUp.besidePlayer(follower, AgentFollowCatchUp.besideOffset(plugin)));
                    AgentFollowTuning.applyForCurrentEntity(plugin, npc);
                    AgentFollowTuning.scheduleFollowAndApplyTraits(plugin, npc, follower);
                }
                return;
            }
        }
    }

    //Run code when your trait is attached to a NPC.
    //This is called BEFORE onSpawn, so npc.getEntity() will return null
    //This would be a good place to load configurable defaults for new NPCs.
    @Override
    public void onAttach() {
        plugin.getServer().getLogger().info(npc.getName() + " has been assigned Rebuild Trait!");
    }

    // Run code when the NPC is despawned. This is called before the entity actually despawns so npc.getEntity() is still valid.
    @Override
    public void onDespawn() {
    }

    //Run code when the NPC is spawned. Note that npc.getEntity() will be null until this method is called.
    //This is called AFTER onAttach and AFTER Load when the server is started.
    @Override
    public void onSpawn() {

    }

    //run code when the NPC is removed. Use this to tear down any repeating tasks.
    @Override
    public void onRemove() {
    }


}

