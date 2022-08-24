package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.RefreshLeader;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class Cleanup extends AgendaSubcommandData {
    public Cleanup() {
        super(Constants.CLEANUP, "Agenda phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Map activeMap = getActiveMap();
               LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player player : players.values()) {
            player.cleanExhaustedPlanets();
        }
    }
}
