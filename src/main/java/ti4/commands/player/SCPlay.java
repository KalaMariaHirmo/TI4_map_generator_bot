package ti4.commands.player;

import java.util.*;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Which SC to play. If you have more than 1 SC, this is mandatory"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);

        Helper.checkThreadLimitAndArchive(event.getGuild());

        MessageChannel eventChannel = event.getChannel();
        MessageChannel mainGameChannel = activeGame.getMainGameChannel() == null ? eventChannel : activeGame.getMainGameChannel();

        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        LinkedHashSet<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            sendMessage("No SC has been selected");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { //Only one SC selected
            sendMessage("Player has more than one SC. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToPlay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        playSC(event, scToPlay, activeGame, mainGameChannel, player);
    }
    public void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game activeGame, MessageChannel mainGameChannel, Player player) {
        playSC(event, scToPlay, activeGame, mainGameChannel, player, false);
    }

    public void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game activeGame, MessageChannel mainGameChannel, Player player, boolean winnuHero) {
        Integer scToDisplay = scToPlay;
        String pbd100group = null;
        boolean pbd100or500 = "pbd100".equals(activeGame.getName()) || "pbd500".equals(activeGame.getName());
        if (pbd100or500) {
            scToDisplay = scToPlay / 10;
            int groupNum = scToPlay % 10;
            switch (groupNum) {
                case 1 -> pbd100group = "A";
                case 2 -> pbd100group = "B";
                case 3 -> pbd100group = "C";
                case 4 -> pbd100group = "D";
                default -> pbd100group = "Unknown";
            }
        }

        if (activeGame.getPlayedSCs().contains(scToPlay) && !winnuHero) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"SC already played");
            return;
        }

        if(!winnuHero){
            activeGame.setSCPlayed(scToPlay, true);
        }
        Helper.checkThreadLimitAndArchive(event.getGuild());
        String categoryForPlayers = Helper.getGamePing(event, activeGame);
        //String message = "Strategy card " + Helper.getEmojiFromDiscord(emojiName) + Helper.getSCAsMention(activeMap.getGuild(), scToDisplay) + (pbd100or500 ? " Group " + pbd100group : "") + " played by " + Helper.getPlayerRepresentation(player, activeMap) + "\n\n";
        StringBuilder scMessageBuilder = new StringBuilder();
        scMessageBuilder.append(Helper.getSCRepresentation(activeGame, scToPlay));
        if (pbd100or500) {
            scMessageBuilder
                    .append(" Group ")
                    .append(pbd100group);
        }
        scMessageBuilder
                .append(" played");
        if (!activeGame.isFoWMode()) {
            scMessageBuilder.append(" by ")
                    .append(Helper.getPlayerRepresentation(player, activeGame));
        }
        scMessageBuilder.append(".\n\n");
        String message = scMessageBuilder.toString();

        if (!categoryForPlayers.isEmpty()) {
            message += categoryForPlayers + "\n";
        }
        message += "Indicate your choice by pressing a button below and post additional details in the thread.";

        String scName = Helper.getSCName(scToDisplay, activeGame).toLowerCase();
        if(winnuHero){
            scName = scName+"WinnuHero";
        }
        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-" + scName + (pbd100or500 ? "-group_" + pbd100group : "");
    
        TextChannel textChannel = (TextChannel) mainGameChannel;

        for (Player player2 : activeGame.getPlayers().values()) {
            if (!player2.isRealPlayer() || winnuHero) {
                continue;
            }
            String faction = player2.getFaction();
            if (faction == null || faction.isEmpty() || "null".equals(faction)) continue;
            player2.removeFollowedSC(scToPlay);
        }

        if (activeGame.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE)) {
            MessageHelper.sendFileToChannel(mainGameChannel, Helper.getSCImageFile(scToDisplay, activeGame), true);
            //MessageHelper.sendMessageToChannel(mainGameChannel, Helper.getSCImageLink(scToDisplay, activeMap));
        }
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);

        // GET BUTTONS
        ActionRow actionRow = null;
        List<Button> scButtons = new ArrayList<>(getSCButtons(scToDisplay, activeGame));
        if (!activeGame.isHomeBrewSCMode() && !activeGame.isFoWMode() && scToDisplay == 7 && Helper.getPlayerFromAbility(activeGame, "propagation") != null) {
            scButtons.add(Button.secondary("nekroFollowTech", "Get CCs").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        }
        if (!scButtons.isEmpty()) actionRow = ActionRow.of(scButtons);
        if (actionRow != null) baseMessageObject.addComponents(actionRow);

        mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            Emoji reactionEmoji = Helper.getPlayerEmoji(activeGame, player, message_);
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
                player.addFollowedSC(scToPlay);
            }

            if (activeGame.isFoWMode()) {
                // in fow, send a message back to the player that includes their emoji
                String response = "SC played.";
                response += reactionEmoji != null ? " " + reactionEmoji.getFormatted() : "\nUnable to generate initial reaction, please click \"Not Following\" to add your reaction.";
                MessageHelper.sendPrivateMessageToPlayer(player, activeGame, response);
            } else {
                // only do thread in non-fow games
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                threadChannel.queue(m5 -> {
                    List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                    if (threadChannels != null) {
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                if (scToPlay == 5) {
                                    Button transaction = Button.primary("transaction", "Transaction");
                                    scButtons.add(transaction);
                                }
                                MessageHelper.sendMessageToChannelWithButtons(threadChannel_, "These buttons will work inside the thread", scButtons);
                            }
                        }
                    }
                });

            }
        });

        // POLITICS - SEND ADDITIONAL ASSIGN SPEAKER BUTTONS
        if (scToPlay == 3) {
            String assignSpeakerMessage = Helper.getPlayerRepresentation(player, activeGame) + ", please, before you draw your action cards or look at agendas, click a faction below to assign Speaker " + Emojis.SpeakerToken;

            List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons(activeGame);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), assignSpeakerMessage, assignSpeakerActionRow);
        }

        if (scToPlay == 3 && !activeGame.isHomeBrewSCMode()) {
             String assignSpeakerMessage2 = Helper.getPlayerRepresentation(player, activeGame) + " after assigning speaker, Use this button to draw agendas into your cards info thread.";
            
            List<Button> drawAgendaButton = new ArrayList<>();
            Button draw2Agenda = Button.success("FFCC_"+player.getFaction()+"_"+"drawAgenda_2", "Draw 2 agendas");
            drawAgendaButton.add(draw2Agenda);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), assignSpeakerMessage2, drawAgendaButton);

        }

        if(scToPlay != 1 ){
            //"scepterE_follow_") || buttonID.startsWith("mahactA_follow_")){
            
            Button emelpar = Button.danger("scepterE_follow_"+scToPlay, "Exhaust Scepter of Emelpar");
            Button mahactA = Button.danger("mahactA_follow_"+scToPlay, "Use Mahact Agent").withEmoji(Emoji.fromFormatted(Emojis.Mahact));
            for (Player player3 : activeGame.getPlayers().values()) {
                if (player3 == player) {
                    continue;
                }
                List<Button> empNMahButtons = new ArrayList<>();
                Button deleteB = Button.danger("deleteButtons", "Delete These Buttons");
                empNMahButtons.add(deleteB);
                if (player3.hasRelic("emelpar") && !player3.getExhaustedRelics().contains("emelpar")) {
                    empNMahButtons.add(0,emelpar);    
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(), Helper.getPlayerRepresentation(player3, activeGame, activeGame.getGuild(), true)+" You can follow SC #"+scToPlay+" with the scepter of emelpar", empNMahButtons);
                }
                if (player3.hasUnexhaustedLeader("mahactagent") && ButtonHelper.getTilesWithYourCC(player, activeGame, event).size() > 0 && !winnuHero) {
                    empNMahButtons.add(0,mahactA);
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(), Helper.getPlayerRepresentation(player3, activeGame, activeGame.getGuild(), true)+" You can follow SC #"+scToPlay+" with mahact agent", empNMahButtons);
                }
            }
        }

        List<Button> conclusionButtons = new ArrayList<>();
        String finChecker = "FFCC_"+player.getFaction() + "_";
        Button endTurn = Button.danger(finChecker+"turnEnd", "End Turn");
        Button deleteButton = Button.danger("doAnotherAction", "Do Another Action");
        conclusionButtons.add(endTurn);
        if (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size()> 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability ("+(ButtonHelper.getEndOfTurnAbilities(player, activeGame).size()- 1)+")"));
        }
        conclusionButtons.add(deleteButton);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn or take another action.", conclusionButtons);

        if (player.ownsPromissoryNote("acq") && scToPlay != 1 && !winnuHero) {
            for (Player player2 : activeGame.getPlayers().values()) {
                if (!player2.getPromissoryNotes().isEmpty()) {
                    for (String pn : player2.getPromissoryNotes().keySet()) {
                        if (!player2.ownsPromissoryNote("acq") && "acq".equalsIgnoreCase(pn)) {
                            String acqMessage = Helper.getPlayerRepresentation(player2, activeGame, event.getGuild(), true) + " reminder you can use Winnu's PN!";
                            if (activeGame.isFoWMode()) {
                                MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), acqMessage);
                            } else {
                                MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(), acqMessage);
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Button> getSCButtons(int sc, Game activeGame) {

        if (activeGame.isHomeBrewSCMode()) {
            return getGenericButtons(sc);
        }
        return switch (sc) {
            case 1 -> getLeadershipButtons();
            case 2 -> getDiplomacyButtons();
            case 3 -> getPoliticsButtons();
            case 4 -> getConstructionButtons();
            case 5 -> getTradeButtons();
            case 6 -> getWarfareButtons();
            case 7 -> getTechnologyButtons();
            case 8 -> getImperialButtons();
            default -> getGenericButtons(sc);
        };
    }

    
    private List<Button> getLeadershipButtons() {
        Button followButton = Button.success("sc_leadership_follow", "SC Follow");
        Button leadershipGenerateCCButtons = Button.success("leadershipGenerateCCButtons", "Gain CCs");
        Button exhaust = Button.danger("leadershipExhaust", "Exhaust Planets");
        Button noFollowButton = Button.primary("sc_no_follow_1", "Not Following");
        return List.of(followButton, exhaust,leadershipGenerateCCButtons,noFollowButton);
    }

    private List<Button> getDiplomacyButtons() {
        Button followButton = Button.success("sc_follow_2", "Spend A Strategy CC");
        Button diploSystemButton = Button.primary("diploSystem", "Diplo a System");
        Button refreshButton = Button.success("diploRefresh2", "Ready 2 Planets");

        Button noFollowButton = Button.danger("sc_no_follow_2", "Not Following");
        return List.of(followButton,diploSystemButton, refreshButton, noFollowButton);
    }

    private List<Button> getPoliticsButtons() {
        Button followButton = Button.success("sc_follow_3", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_3", "Not Following");
        Button draw_2_ac = Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        return List.of(followButton, noFollowButton, draw_2_ac);
    }

    private static List<Button> getPoliticsAssignSpeakerButtons(Game activeGame) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(activeGame.getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    if(!activeGame.isFoWMode()){
                        Button button = Button.secondary(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, " ");
                        String factionEmojiString = player.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                        assignSpeakerButtons.add(button);
                    }else{
                        Button button = Button.secondary(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, player.getColor());
                        assignSpeakerButtons.add(button);
                    }
                    
                }
            }
        }
        return assignSpeakerButtons;
    }

    private List<Button> getConstructionButtons() {
        Button followButton = Button.success("sc_follow_4", "Spend A Strategy CC");
        Button sdButton = Button.success("construction_sd", "Place A SD");
        sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
        Button pdsButton = Button.success("construction_pds", "Place a PDS");

        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
        Button noFollowButton = Button.primary("sc_no_follow_4", "Not Following");
        return List.of(followButton, sdButton, pdsButton, noFollowButton);
    }

    private List<Button> getTradeButtons() {
        Button trade_primary = Button.success("trade_primary", "Resolve Primary");
        Button followButton = Button.success("sc_trade_follow", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_5", "Not Following");
        Button refresh_and_wash = Button.secondary("sc_refresh_and_wash", "Replenish and Wash").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button refresh = Button.secondary("sc_refresh", "Replenish Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
        return List.of(trade_primary, followButton, noFollowButton, refresh, refresh_and_wash);
    }

    private List<Button> getWarfareButtons() {
        Button warfarePrimary= Button.primary("primaryOfWarfare", "Do Warfare Primary");
        Button followButton = Button.success("sc_follow_6", "Spend A Strategy CC");
        Button homeBuild = Button.success("warfareBuild", "Build At Home");
        Button noFollowButton = Button.primary("sc_no_follow_6", "Not Following");
        return List.of(warfarePrimary,followButton,homeBuild, noFollowButton);
    }

    private List<Button> getTechnologyButtons() {
        Button followButton = Button.success("sc_follow_7", "Spend A Strategy CC");
        Button getTech = Button.success("acquireATech", "Get a Tech");
        Button noFollowButton = Button.primary("sc_no_follow_7", "Not Following");
        return List.of(followButton, getTech ,noFollowButton);
    }

    private List<Button> getImperialButtons() {
        Button followButton = Button.success("sc_follow_8", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_8", "Not Following");
        Button draw_so = Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective));
        Button scoreImperial = Button.secondary("score_imperial", "Score Imperial").withEmoji(Emoji.fromFormatted(Emojis.MecatolRex));
        Button scoreAnObjective = Button.secondary("scoreAnObjective", "Score A Public").withEmoji(Emoji.fromFormatted(Emojis.Public1));

        return List.of(followButton, noFollowButton, draw_so, scoreImperial, scoreAnObjective);
    }

    private List<Button> getGenericButtons(int sc) {
        Button followButton = Button.success("sc_follow_"+sc, "Spend A Strategy CC" );
        Button noFollowButton = Button.primary("sc_no_follow_"+sc, "Not Following");
        return List.of(followButton, noFollowButton);
    }
}
