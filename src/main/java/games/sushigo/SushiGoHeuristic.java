package games.sushigo;
import core.AbstractGameState;
import core.actions.AbstractAction;
import core.components.Card;
import core.components.Deck;
import core.interfaces.IStateHeuristic;
import evaluation.TunableParameters;
import games.sushigo.actions.PlayCardAction;
import games.sushigo.cards.SGCard;
import utilities.Utils;

import java.util.ArrayList;
import java.util.Optional;

public class SushiGoHeuristic extends TunableParameters implements IStateHeuristic {

    public SGCard lastCardPlayed = null;
    public SushiGoHeuristic() {
    }

    public void setLastCardPlayed(SGCard card)
    {
        lastCardPlayed = card;
    }

    @Override
    public void _reset() {
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        //This is a simple win-lose heuristic.
        SGGameState sgs = (SGGameState)gs;
        Utils.GameResult playerResult = sgs.getPlayerResults()[playerId];

        double bestOppScore = 0;
        for (int pID = 0; pID < sgs.getNPlayers(); pID++) {
            if(pID != playerId) {
                double oppScore = sgs.getPlayerScore()[pID] + sgs.getPlayerScoreToAdd(pID) + evaluateCardState(sgs,pID);
                if(oppScore > bestOppScore)
                {
                    bestOppScore = oppScore;
                }
            }
        }
        double playerScore = sgs.getPlayerScore()[playerId] + sgs.getPlayerScoreToAdd(playerId)  + evaluateCardState(sgs, playerId);
        if (gs.isNotTerminal() && sgs.playerHands.get(playerId).getSize() != 0)
            return sigmoid((playerScore - bestOppScore) / 4);
        return gs.getPlayerResults()[playerId].value;
    }

    public double evaluateCardState(SGGameState sgs, int playerId)
    {
        double value = 0;
        Deck<SGCard> playerField = sgs.playerFields.get(playerId).copy();
        int nextHandIndex = sgs.getCurrentPlayer();
        if(nextHandIndex != sgs.getTurnOrder().getFirstPlayer() && sgs.getCurrentPlayer() != playerId && sgs.playerCardPicks[playerId] != -1)
        {
            playerField.add(sgs.playerHands.get(playerId).getComponents().get(sgs.playerCardPicks[playerId]));
            nextHandIndex++;
            if(nextHandIndex > sgs.getNPlayers() - 1)
            {
                nextHandIndex = 0;
            }
            //lastCardPlayed = sgs.getHistory().
        }
        Deck<SGCard> playerHand = sgs.playerHands.get(nextHandIndex);

        Deck<SGCard> fieldMinusLastPlayed = playerField.copy();
        if(lastCardPlayed != null && playerField.get(0) != lastCardPlayed)
        {
            playerField.add(lastCardPlayed);
        }

        if(playerField.getSize() > 0)
        {
            fieldMinusLastPlayed.remove(0);
        }
        if(sgs.getPlayerWasabiAvailable(playerId) > 0)
        {
            if(fieldMinusLastPlayed.stream().filter(o -> o.type == SGCard.SGCardType.Wasabi).count() != sgs.getPlayerChopSticksAmount(playerId))
            {
                value += 3.0;
            }
            else
            {
                value += 1.0;
            }
        }

        if(sgs.getPlayerChopSticksAmount(playerId) > 0)
        {
            if(fieldMinusLastPlayed.stream().filter(o -> o.type == SGCard.SGCardType.Chopsticks).count() != sgs.getPlayerChopSticksAmount(playerId))
            {
                value += 4.0;
            }
            else
            {
                value += 1.0;
            }
        }


        if(sgs.getPlayerTempuraAmount(playerId) % 2 > 0)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Tempura))
            {
                value += 2.5;
            }
        }

        if(sgs.getPlayerSashimiAmount(playerId) == 2)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Sashimi))
            {
                value += 6.66;
            }
        }

        if(sgs.getPlayerDumplingAmount(playerId) > 1)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Dumpling))
            {
                value += sgs.getPlayerDumplingAmount(playerId) / 2.0;
            }
        }

        float currentMakiPos = sgs.getNPlayers();
        for(int id = 0; id < sgs.getNPlayers(); id++)
        {
            if(id != playerId)
            {
                if(getMakiCount(sgs, playerId) <= getMakiCount(sgs, id))
                {
                    currentMakiPos--;
                }
            }

        }

//        value += currentMakiPos * 3.0 * ((float)(sgs.cardAmount - sgs.playerHands.get(playerId).getComponents().size()) / (float)sgs.cardAmount);
//
//        float currentPuddingPos = sgs.getNPlayers();
//        for(int id = 0; id < sgs.getNPlayers(); id++)
//        {
//            if(id != playerId)
//            {
//                if(sgs.getPlayerField())
//                {
//                    currentMakiPos--;
//                }
//            }
//
//        }
//        if(sgs.playerChopsticksActivated[playerId] == true)
//        {
//            value += 10;
//        }
        return value;
    }

    private double sigmoid(double x)
    {
        return 1 / (1 + Math.exp(-x));
    }

    private int getMakiCount(SGGameState sgs, int playerId)
    {
        int makiPoints = 0;
        for (int j = 0; j < sgs.getPlayerFields().get(playerId).getSize(); j++) {
            switch (sgs.getPlayerFields().get(playerId).get(j).type) {
                case Maki_1:
                    makiPoints += 1;
                    break;
                case Maki_2:
                    makiPoints += 2;
                    break;
                case Maki_3:
                    makiPoints += 3;
                    break;
                default:
                    break;
            }
        }
        return makiPoints;
    }

    /**
     * Return a copy of this game parameters object, with the same parameters as in the original.
     *
     * @return - new game parameters object.
     */
    @Override
    protected SushiGoHeuristic _copy() {
        return new SushiGoHeuristic();
    }

    /**
     * Checks if the given object is the same as the current.
     *
     * @param o - other object to test equals for.
     * @return true if the two objects are equal, false otherwise
     */
    @Override
    protected boolean _equals(Object o) {
        return o instanceof SushiGoHeuristic;
    }

    /**
     * @return Returns Tuned Parameters corresponding to the current settings
     * (will use all defaults if setParameterValue has not been called at all)
     */
    @Override
    public SushiGoHeuristic instantiate() {
        return this._copy();
    }
}
