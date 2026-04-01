package edu.whimc.overworld_agent.dialoguetemplate;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.dialoguetemplate.events.BuildAssessEvent;
import edu.whimc.overworld_agent.dialoguetemplate.models.BuildTemplate;
import edu.whimc.overworld_agent.utils.Utils;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.FollowTrait;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class BuilderDialogue {
    private Player player;
    private OverworldAgent plugin;
    private SpigotCallback spigotCallback;
    private static final String BULLET = "\u2022";
    private boolean makingTemplate;
    private boolean embodied;
    private int id;
    private Logger log;
    public BuilderDialogue(OverworldAgent plugin, Player player, boolean embodied){
        this.plugin = plugin;
        this.player = player;
        this.spigotCallback = new SpigotCallback(plugin);
        this.makingTemplate = false;
        this.embodied = embodied;
        log = Logger.getLogger("Minecraft");
        id = -1;
    }

    public void doDialogue(){
        HashMap<Player,List<BuildTemplate>> templates = plugin.getBuildTemplates();
        Utils.msgNoPrefix(player, "&lWhat do you want to do?", "");
        if(player.isOp()){
            sendComponent(
                    player,
                    "&8" + BULLET + "&f&nI want to demo a build using the build ID!",
                    "&aClick here to demo a build!",
                    p -> this.plugin.getSignMenuFactory()
                            .newMenu(Collections.singletonList(Utils.color("&f&nBuild ID")))
                            .reopenIfFail(true)
                            .response((signPlayer, strings) -> {
                                String response = StringUtils.join(Arrays.copyOfRange(strings, 0, strings.length), ' ').trim();
                                if (response.isEmpty()) {
                                    return false;
                                }
                                int buildID;
                                try {
                                    buildID = Integer.parseInt(response);
                                } catch(NumberFormatException e){
                                    return false;
                                }
                                    plugin.getQueryer().getBuildTemplate(buildID, player, template -> {
                                        if(template != null) {
                                            this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Demo Build"), buildID, id -> {
                                            BuildTemplate bt = (BuildTemplate) template;
                                            bt.build(embodied);
                                            this.id = id;
                                            });
                                        } else {
                                            player.sendMessage("Template with ID " + buildID +" does not exist!");
                                        }
                                });
                                this.spigotCallback.clearCallbacks(player);
                                return true;
                            })
                            .open(p)
            );
            sendComponent(
                    player,
                    "&8" + BULLET + "&f&nI want to reset player templates!",
                    "&aClick here to reset player or all build templates!",
                    p -> this.plugin.getSignMenuFactory()
                            .newMenu(Collections.singletonList(Utils.color("&f&nPlayer name or all")))
                            .reopenIfFail(true)
                            .response((signPlayer, strings) -> {
                                String response = StringUtils.join(Arrays.copyOfRange(strings, 0, strings.length), ' ').trim();
                                if (response.isEmpty()) {
                                    return false;
                                }
                                this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Reset templates"), -1, id -> {
                                    this.id = id;
                                    if (response.equalsIgnoreCase("all")) {
                                        plugin.resetTemplates("all");
                                        player.sendMessage("All build templates have been reset!");
                                    } else if (Bukkit.getPlayer(response) != null) {
                                        if (templates.get(Bukkit.getPlayer(response)) != null) {
                                            plugin.resetTemplates(response);
                                            player.sendMessage("Templates for " + response + " have been reset!");
                                        } else {
                                            player.sendMessage(response + "does not have any templates!");
                                        }
                                    } else {
                                        player.sendMessage(response + "does exist!");
                                    }
                                });
                                this.spigotCallback.clearCallbacks(player);
                                return true;
                            })
                            .open(p)
            );
        }
        if (!makingTemplate) {
            sendComponent(
                    player,
                    "&8" + BULLET + "&f&nI want to start a template!",
                    "&aClick here to start a build template!",
                    p -> this.plugin.getSignMenuFactory()
                            .newMenu(Collections.singletonList(Utils.color("")))
                            .reopenIfFail(true)
                            .response((signPlayer, strings) -> {
                                String response = StringUtils.join(Arrays.copyOfRange(strings, 0, strings.length), ' ').trim();
                                if (response.isEmpty()) {
                                    return false;
                                }
                                if (templates.get(player) != null) {
                                    for (BuildTemplate template : templates.get(player)) {
                                        if (template.getName().equalsIgnoreCase(response)) {
                                            player.sendMessage("A build template with this name already exists! Templates must have different names.");
                                            this.spigotCallback.clearCallbacks(player);
                                            return true;
                                        }
                                    }
                                }
                                this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Start Template"), -1, id -> {
                                    this.id = id;
                                    BuildTemplate template = new BuildTemplate(plugin, player, response, new Timestamp(System.currentTimeMillis()), null, player);
                                    plugin.addTemplate(player, template);
                                    plugin.addInProgressTemplate(player, this);
                                    player.sendMessage("You just started a build template called " + template.getName());
                                    this.makingTemplate = true;
                                });
                                this.spigotCallback.clearCallbacks(player);
                                return true;
                            })
                            .open(p)
            );
        } else {
            sendComponent(
                    player,
                    "&8" + BULLET + "&f&nI want to finish my template!",
                    "&aClick here to finish my build template!",
                    p -> {
                        plugin.removeInProgressTemplate(player);
                        for (BuildTemplate template : templates.get(player)) {
                            if (template.getEndTime() == null) {
                                template.setEndTime(new Timestamp(System.currentTimeMillis()));
                                this.plugin.getQueryer().storeNewTemplate(template, buildId -> {
                                    this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Finish Template"), buildId, id -> {
                                        this.id = id;
                                        template.setID(buildId);
                                        player.sendMessage("You just created a build template called " + template.getName());
                                    });
                                });
                                break;
                            }
                        }
                        this.makingTemplate = false;
                        this.spigotCallback.clearCallbacks(player);
                    });
            sendComponent(
                    player,
                    "&8" + BULLET + "&f&nI want to cancel my template!",
                    "&aClick here to cancel my template!",
                    p -> {
                        this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Cancel Template"), -1, id -> {
                            this.id = id;
                            plugin.removeInProgressTemplate(player);
                            for (int k = 0; k < templates.get(player).size(); k++) {
                                BuildTemplate template = templates.get(player).get(k);
                                if (template.getEndTime() == null) {
                                    templates.get(player).remove(k);
                                    player.sendMessage("You just canceled a build template called " + template.getName());
                                    break;
                                }
                            }
                            this.makingTemplate = false;
                            this.spigotCallback.clearCallbacks(player);
                        });

                    });
        }

        if (templates.get(player) != null) {
            List<BuildTemplate> builds = templates.get(player);
            List<BuildTemplate> finishedBuilds = new ArrayList<>();
            for (BuildTemplate build : builds) {
                if (build.getEndTime() != null) {
                    finishedBuilds.add(build);
                }
            }
            if (finishedBuilds.size() > 0) {
                sendComponent(
                        player,
                        "&8" + BULLET + "&f&nI want my agent to build something!",
                        "&aClick here to have your agent build something!",
                        p -> {
                            this.spigotCallback.clearCallbacks(player);
                            Utils.msgNoPrefix(player, "&lClick the template you want to build:", "");
                            for (BuildTemplate finished : finishedBuilds) {
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + finished.getName(),
                                        "&aClick here to select \"&r" + finished.getName() + "&a\"",
                                        l -> {
                                            this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Build Template"), finished.getID(), id -> {
                                                this.id = id;
                                                finished.build(embodied);
                                                this.spigotCallback.clearCallbacks(player);
                                            });
                                        });
                            }
                        });
                /**
                sendComponent(
                        player,
                        "&8" + BULLET + "&f&nI want to delete a template!",
                        "&aClick here to delete a build templates!",
                        p -> {
                            for (BuildTemplate finished : finishedBuilds) {
                                sendComponent(
                                        player,
                                        "&8" + BULLET + " &r" + finished.getName(),
                                        "&aClick here to select \"&r" + finished.getName() + "&a\"",
                                        l -> {
                                            this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Remove Template"), finished.getID(), id -> {
                                                plugin.removeTemplate(player, finished);
                                                player.sendMessage(finished.getName() + " has been removed!");
                                                this.spigotCallback.clearCallbacks(player);
                                            });
                                        });
                            }
                        });
                 */
            }
        }
        sendComponent(
                player,
                "&8" + BULLET + "&f&nI want you to give me feedback on my base!",
                "&aClick here to get feedback!",
                p -> {
                    Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "assess-habitat " + player.getName());
                    this.spigotCallback.clearCallbacks(player);
                });

        NPC agent = plugin.getAgents().get(player.getName());
        if(embodied) {
            if (agent != null && agent.getOrAddTrait(FollowTrait.class).isActive()) {
                sendComponent(
                        player,
                        "&8" + BULLET + "&f&nI want you to stay here!",
                        "&aClick here to make me wait here while you build!",
                        p -> {
                            this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Stationary Agent"), -1, id -> {
                                this.id = id;
                                agent.getOrAddTrait(FollowTrait.class).follow(null);
                                player.sendMessage("I will wait here until you need me again!");
                                this.spigotCallback.clearCallbacks(player);
                            });
                        });
            } else {
                sendComponent(
                        player,
                        "&8" + BULLET + "&f&nI want you to follow me!",
                        "&aClick here to make me follow you while you build!",
                        p -> {
                            this.plugin.getQueryer().storeNewBuildInteraction(new Interaction(plugin, player, "Following Agent"), -1, id -> {
                                this.id = id;
                                agent.getOrAddTrait(FollowTrait.class).follow(player);
                                player.sendMessage("Let's go continue building!");
                                this.spigotCallback.clearCallbacks(player);
                            });

                        });
            }
        }
    }

    public Player getPlayer(){return player;}
    public int getId(){return id;}
    private void sendComponent(Player player, String text, String hoverText, Consumer<Player> onClick) {
        player.spigot().sendMessage(createComponent(text, hoverText, onClick));
    }

    private TextComponent createComponent(String text, String hoverText, Consumer<Player> onClick) {
        TextComponent message = new TextComponent(Utils.color(text));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Utils.color(hoverText)).create()));
        addCallback(message, this.player.getUniqueId(), onClick);
        return message;
    }

    private void addCallback(TextComponent component, UUID playerUUID, Consumer<Player> onClick) {
        this.spigotCallback.createCommand(playerUUID, component, onClick);
    }
}
