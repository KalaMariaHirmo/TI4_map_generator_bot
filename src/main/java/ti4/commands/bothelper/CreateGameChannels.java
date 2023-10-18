package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.game.GameCreate;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CreateGameChannels extends BothelperSubcommandData {
    public CreateGameChannels() {
        super(Constants.CREATE_GAME_CHANNELS, "Create Role and Game Channels for a New Game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel - e.g. pbd###-fun-name-goes-here").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName - this will be the game owner, who will complete /game setup").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Override default game/role name (next pbd###)"));
        addOptions(new OptionData(OptionType.STRING, Constants.CATEGORY, "Override default Category #category-name (PBD #XYZ-ZYX)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild;

        //GAME NAME
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        String gameName;
        if (gameNameOption != null) {
            gameName = gameNameOption.getAsString();
            if (gameOrRoleAlreadyExists(gameName)) {
                sendMessage("Role or Game: **" + gameName + "** already exists accross all supported servers. Try again with a new name.");
                return;
            }
        } else {
            gameName = getNextGameName();
        }

        //CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = event.getOption(Constants.CATEGORY, null, OptionMapping::getAsString);
        Category categoryChannel = null;
        if (categoryChannelName != null && !categoryChannelName.isEmpty()) {
            List<Category> categoriesWithName = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false);
            if (categoriesWithName.size() > 1) {
                sendMessage("Too many categories with this name!!");
                return;
            } else if (categoriesWithName.isEmpty()) {
                sendMessage("Category not found");
                return;
            } else {
                categoryChannel = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false).get(0);
            }
        } else { //CATEGORY WAS NOT PROVIDED, FIND ONE
            categoryChannelName = getCategoryNameForGame(gameName);
            if (categoryChannelName == null) {
                sendMessage("Please provide a category name for this game");
                return;
            }
            List<Category> categories = getAllAvailablePBDCategories();
            for (Category category : categories) {
                if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                    categoryChannel = category;
                    break;
                }
            }
            if (categoryChannel == null) {
                sendMessage("Could not automatically find a category that begins with **" + categoryChannelName + "** - Please create this category.");
                return;
            }
        }

        //CHECK IF CATEGORY EXISTS
        if (categoryChannel == null || categoryChannel.getType() != ChannelType.CATEGORY) {
            sendMessage("Category: **" + categoryChannel.getName() + "** does not exist. Create the category or pick a different category, then try again.");
            return;
        }

        //SET GUILD BASED ON CATEGORY SELECTED
        guild = categoryChannel.getGuild();

        //CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!serverCanHostNewGame(guild)) {
            sendMessage("Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        //CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            sendMessage("Category: **" + category.getName() + "** is full on server **" + guild.getName() + "**. Create a new category then try again.");
            return;
        } else if (category.getChannels().size() > 45) {
            String message = "Warning: Category: **" + category.getName() + "** is almost full on server **" + guild.getName() + "**.";
            TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("bothelper-lounge", true).get(0);
            if (bothelperLoungeChannel != null) {
                MessageHelper.sendMessageToChannel(bothelperLoungeChannel, message);
            } else {
                BotLogger.log(event, message);
            }
        }

        //PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
                if (gameOwner == null) gameOwner = member;
            } else {
                break;
            }
        }

        //CHECK IF GUILD HAS ALL PLAYERS LISTED
        List<String> guildMemberIDs = guild.getMembers().stream().map(ISnowflake::getId).toList();
        List<Member> missingMembers = new ArrayList<>();
        for (Member member : members) {
            if (!guildMemberIDs.contains(member.getId())) {
                missingMembers.add(member);
            }
        }
        if (missingMembers.size() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(
                "### Sorry for the inconvenience!\nDue to Discord's limits on Role/Channel/Thread count, we need to create this game on another server.\nPlease use the invite below to join our **");
            sb.append(guild.getName()).append("** server.\n");
            sb.append(Helper.getGuildInviteURL(guild)).append("\n");
            sb.append("The following players need to join the server:\n");
            for (Member member : missingMembers) {
                sb.append("> ").append(member.getAsMention()).append("\n");
            }
            sb.append("You will be automatically added to the game channels when you join the server.");
            sendMessage(sb.toString());
        }

        //CREATE ROLE
        Role role = guild.createRole()
            .setName(gameName)
            .setMentionable(true)
            .complete();

        //ADD PLAYERS TO ROLE
        for (Member member : members) {
            if (missingMembers.contains(member)) continue; //skip members who aren't on the new server yet
            guild.addRoleToMember(member, role).complete();
        }

        //CREATE GAME
        Game newGame = GameCreate.createNewGame(event, gameName, gameOwner);

        //ADD PLAYERS
        for (Member member : members) {
            newGame.addPlayer(member.getId(), member.getEffectiveName());
        }

        //CREATE CHANNELS
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString().replaceAll(" ", "-");
        newGame.setCustomName(gameFunName);
        String newChatChannelName = gameName + "-" + gameFunName;
        String newActionsChannelName = gameName + Constants.ACTIONS_CHANNEL_SUFFIX;
        String newBotThreadName = gameName + Constants.BOT_CHANNEL_SUFFIX;
        long gameRoleID = role.getIdLong();
        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();

        // CREATE TABLETALK CHANNEL
        TextChannel chatChannel = guild.createTextChannel(newChatChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();
        newGame.setTableTalkChannelID(chatChannel.getId());

        // CREATE ACTIONS CHANNEL
        TextChannel actionsChannel = guild.createTextChannel(newActionsChannelName, category)
            .syncPermissionOverrides()
            .addRolePermissionOverride(gameRoleID, permission, 0)
            .complete();
        newGame.setMainGameChannelID(actionsChannel.getId());

        // CREATE BOT/MAP THREAD
        ThreadChannel botThread = actionsChannel.createThreadChannel(newBotThreadName)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
            .complete();
        newGame.setBotMapUpdatesThreadID(botThread.getId());

        // INTRODUCTION TO TABLETALK CHANNEL
        String tabletalkGetStartedMessage = role.getAsMention() + " - table talk channel\n" +
            "This channel is for typical over the table converstion, as you would over the table while playing the game in real life.\n" +
            "If this group has agreed to whispers (secret conversations), you can create private threads off this channel.\n" +
            "Typical things that go here are: general conversation, deal proposals, memes - everything that isn't either an actual action in the game or a bot command\n";
        MessageHelper.sendMessageToChannelAndPin(chatChannel, tabletalkGetStartedMessage);

        // INTRODUCTION TO ACTIONS CHANNEL
        String actionsGetStartedMessage = role.getAsMention() + " - actions channel\n" +
            "This channel is for taking actions in the game, primarily using buttons or the odd slash command.\n" +
            "Please keep this channel clear of any chat with other players. Ideally this channel is a nice clean ledger of what has physically happened in the game.\n";
        MessageHelper.sendMessageToChannelAndPin(actionsChannel, actionsGetStartedMessage);

        // INTRODUCTION TO BOT-MAP THREAD
        String botGetStartedMessage = role.getAsMention() + " - bot/map channel\n" +
            "This channel is for bot slash commands and updating the map, to help keep the actions channel clean.\n" +
            "### __Use the following commands to get started:__\n" +
            "> `/game setup` to set player count and additional options\n" +
            "> `/map add_tile_list {mapString}`, replacing {mapString} with a TTPG map string\n" +
            "> `/game set_order` to set the starting speaker order\n" +
            "> `/player setup` to set player faction and colour\n" +
            "> `/tech add` for factions who need to add tech\n" +
            "\n" +
            "### __Other helpful commands:__\n" +
            "> `/game replace` to replace a player in the game with a new one\n";
        MessageHelper.sendMessageToChannelAndPin(botThread, botGetStartedMessage);
        MessageHelper.sendMessageToChannelAndPin(botThread, "Website Live Map: https://ti4.westaddisonheavyindustries.com/game/" + gameName);

        String message = "Role and Channels have been set up:\n" + "> " + role.getName() + "\n" +
            "> " + chatChannel.getAsMention() + "\n" +
            "> " + actionsChannel.getAsMention() + "\n" +
            "> " + botThread.getAsMention() + "\n";
        sendMessage(message);

        GameSaveLoadManager.saveMap(newGame, event);

        //AUTOCLOSE THREAD AFTER RUNNING COMMAND
        if (event.getChannel() instanceof ThreadChannel) {
            ThreadChannel thread = (ThreadChannel) event.getChannel();
            thread.getManager().setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queue();
            thread.getManager().setArchived(true).queue();
            thread.getManager().setArchived(true).queueAfter(5, TimeUnit.MINUTES);
        }

        GameCreate.reportNewGameCreated(newGame);
    }

    private static String getNextGameName() {
        List<Integer> existingNums = getAllExistingPBDNumbers();
        if (existingNums.size() == 0) {
            return "pbd1";
        }
        int nextPBDNumber = Collections.max(getAllExistingPBDNumbers()) + 1;
        return "pbd" + nextPBDNumber;
    }

    private static boolean gameOrRoleAlreadyExists(String name) {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        List<String> gameAndRoleNames = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            //EXISTING ROLE NAMES
            for (Role role : guild.getRoles()) {
                gameAndRoleNames.add(role.getName());
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        Set<String> mapNames = new HashSet<>(GameManager.getInstance().getGameNameToGame().keySet());
        gameAndRoleNames.addAll(mapNames);

        //CHECK
        return mapNames.contains(name);
    }

    private static List<Integer> getAllExistingPBDNumbers() {
        List<Guild> guilds = AsyncTI4DiscordBot.jda.getGuilds();
        List<Integer> pbdNumbers = new ArrayList<>();

        // GET ALL PBD ROLES FROM ALL GUILDS
        for (Guild guild : guilds) {
            System.out.println(guild.getName());
            List<Role> pbdRoles = guild.getRoles().stream()
                .filter(r -> r.getName().startsWith("pbd"))
                .toList();

            //EXISTING ROLE NAMES
            for (Role role : pbdRoles) {
                String pbdNum = role.getName().replace("pbd", "");
                if (Helper.isInteger(pbdNum)) {
                    pbdNumbers.add(Integer.parseInt(pbdNum));
                }
            }
        }

        // GET ALL EXISTING PBD MAP NAMES
        List<String> mapNames = GameManager.getInstance().getGameNameToGame().keySet().stream()
            .filter(mapName -> mapName.startsWith("pbd"))
            .toList();
        for (String mapName : mapNames) {
            String pbdNum = mapName.replace("pbd", "");
            if (Helper.isInteger(pbdNum)) {
                pbdNumbers.add(Integer.parseInt(pbdNum));
            }
        }

        return pbdNumbers;
        //return pbdNumbers.stream().filter(num -> num != 1000).toList();
    }

    private static Guild getNextAvailableServer() {
        if (serverCanHostNewGame(AsyncTI4DiscordBot.guildPrimary)) {
            return AsyncTI4DiscordBot.guildPrimary;
        } else if (serverCanHostNewGame(AsyncTI4DiscordBot.guildSecondary)) {
            return AsyncTI4DiscordBot.guildSecondary;
        } else {
            return null;
        }
    }

    private static boolean serverCanHostNewGame(Guild guild) {
        return guild != null && serverHasRoomForNewRole(guild)
            && serverHasRoomForNewChannels(guild);
    }

    private static boolean serverHasRoomForNewRole(Guild guild) {
        int roleCount = guild.getRoles().size();
        if (roleCount >= 250) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewRole` Cannot create a new role. Server **" + guild.getName() + "** currently has **" + roleCount + "** roles.");
            return false;
        }
        return true;
    }

    private static boolean serverHasRoomForNewChannels(Guild guild) {
        int channelCount = guild.getChannels().size();
        int channelMax = 500;
        int channelsCountRequiredForNewGame = 2;
        if (channelCount > (channelMax - channelsCountRequiredForNewGame)) {
            BotLogger.log("`CreateGameChannels.serverHasRoomForNewChannels` Cannot create new channels. Server **" + guild.getName() + "** currently has " + channelCount + " channels.");
            return false;
        }
        return true;
    }

    public static String getCategoryNameForGame(String gameName) {
        if (!gameName.startsWith("pbd")) return null;
        String gameNumber = StringUtils.substringAfter(gameName, "pbd");
        if (!Helper.isInteger(gameNumber)) return null;
        int gameNum = Integer.parseInt(gameNumber);
        int lowerBound = gameNum - gameNum % 25 + 1;
        int upperBound = lowerBound + 24;
        if (gameNum % 25 == 0) {
            lowerBound = gameNum - 24;
            upperBound = gameNum;
        }
        return "PBD #" + lowerBound + "-" + upperBound;
    }

    public static List<Category> getAllAvailablePBDCategories() {

        return AsyncTI4DiscordBot.jda.getCategories().stream()
            .filter(category -> category.getName().toUpperCase().startsWith("PBD #"))
            .toList();
    }
}
