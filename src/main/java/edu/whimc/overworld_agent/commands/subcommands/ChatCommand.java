package edu.whimc.overworld_agent.commands.subcommands;

import edu.whimc.overworld_agent.OverworldAgent;
import edu.whimc.overworld_agent.commands.AbstractSubCommand;
import edu.whimc.overworld_agent.dialoguetemplate.BuilderDialogue;
import edu.whimc.overworld_agent.dialoguetemplate.Dialogue;
import edu.whimc.overworld_agent.dialoguetemplate.models.DialogueType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatCommand  extends AbstractSubCommand {
    private final String COMMAND = "chat";

    public ChatCommand(OverworldAgent plugin, String baseCommand, String subCommand){
        super(plugin, baseCommand, subCommand);
        super.description("Opens a dialogue menu to chat to your agent");
        super.arguments("");
    }
    /**
     * Creates a dialogue menu to chat with the agent
     * @param sender - Source of the command
     * @param args - Passed command arguments
     * @return if the command was successfully executed
     */
    @Override
    protected boolean onCommand(CommandSender sender, String[] args) {
        Player player;
        boolean text = true;
        boolean embodied = false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be a player");
            return true;
        } else {
            player = (Player) sender;
        }
        if(plugin.getAgentType().equals(DialogueType.GUIDE)){
            plugin.ensureAgentEdits(player);
            Dialogue dialogue = new Dialogue(plugin, player, text, embodied);
            dialogue.doDialogue();
        } else {
            if (plugin.getInProgressTemplates().containsKey(player)) {
                BuilderDialogue bd = plugin.getInProgressTemplates().get(player);
                bd.doDialogue();
            } else {
                BuilderDialogue bd = new BuilderDialogue(plugin, player, embodied);
                bd.doDialogue();
            }
        }
        return true;
    }
}
