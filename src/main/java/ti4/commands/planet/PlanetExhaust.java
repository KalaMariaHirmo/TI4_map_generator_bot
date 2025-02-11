package ti4.commands.planet;

import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
public class PlanetExhaust extends PlanetAddRemove {
    public PlanetExhaust() {
        super(Constants.PLANET_EXHAUST, "Exhaust Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        if(!player.getPlanets().contains(planet)){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation()+" the bot doesnt think you have planet by the name of "+planet);
        }
        if (!player.hasPlanetReady(planet)) return;
        DiscordantStarsHelper.handleOlradinPoliciesWhenExhaustingPlanets(activeGame, player, planet);
        player.exhaustPlanet(planet);
    }
}
