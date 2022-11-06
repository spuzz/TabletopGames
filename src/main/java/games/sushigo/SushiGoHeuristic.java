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

        SGGameState sgs = (SGGameState)gs;

        // Calculate the heuristic score for all other players
        // Take the highest score to use against the players score
        double bestOppScore = 0;
        for (int pID = 0; pID < sgs.getNPlayers(); pID++) {
            // Do not use the player
            if(pID != playerId) {
                // Current score + score waiting after playing a card this turn + evaluation of the current state for combo cards
                double oppScore = sgs.getPlayerScore()[pID] + sgs.getPlayerScoreToAdd(pID) + evaluateCardState(sgs,pID);
                if(oppScore > bestOppScore)
                {
                    bestOppScore = oppScore;
                }
            }
        }

        // Current score + score waiting after playing a card this turn + evaluation of the current state for combo cards
        double playerScore = sgs.getPlayerScore()[playerId] + sgs.getPlayerScoreToAdd(playerId)  + evaluateCardState(sgs, playerId);

        if (gs.isNotTerminal() && sgs.playerHands.get(playerId).getSize() != 0)
            // sigmoid will give a reward of 0.5 for a draw and will increase/decrease faster for small differences in score
            // This will make sure that close scores are more important to MCTS
            return sigmoid((playerScore - bestOppScore) / 10);
        return gs.getPlayerResults()[playerId].value;
    }

    /**
     * Take new hand and field and add score for possible combinations (E.g. 2 Tempura)
     * @return - additional score.
     */
    public double evaluateCardState(SGGameState sgs, int playerId)
    {
        double value = 0;
        Deck<SGCard> playerField = sgs.playerFields.get(playerId).copy();
        // We may be missing the last card played if we are still in the same simultaneous turn
        // Add the last card played if it is not the last card in the field
        if(lastCardPlayed != null && playerField.get(0) != lastCardPlayed)
        {
            playerField.add(lastCardPlayed);
        }
        // Determine if it is not the players action and still the same simultaneous turn
        // If it is we need to assume we will get the hand from the next player
        int nextHandIndex = sgs.getCurrentPlayer();
        if(nextHandIndex != sgs.getTurnOrder().getFirstPlayer() && sgs.getCurrentPlayer() != playerId && sgs.playerCardPicks[playerId] != -1)
        {
            playerField.add(sgs.playerHands.get(playerId).getComponents().get(sgs.playerCardPicks[playerId]));
            nextHandIndex++;
            if(nextHandIndex > sgs.getNPlayers() - 1)
            {
                nextHandIndex = 0;
            }
        }
        Deck<SGCard> playerHand = sgs.playerHands.get(nextHandIndex);
        Deck<SGCard> fieldMinusLastPlayed = playerField.copy();

        if(playerField.getSize() > 0)
        {
            fieldMinusLastPlayed.remove(0);
        }

        // if wasabi available then add additional reward
        // Give less reward if it was not the last card played as it has no value hanging on to it
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

        // if chopsticks available then additional reward
        // Give less reward if it was not the last card played as it has no value hanging on to it
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

        // player has a spare tempura field and a tempura in hand them give an additional reward
        if(sgs.getPlayerTempuraAmount(playerId) % 2 > 0)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Tempura))
            {
                value += 2.5;
            }
        }

        // player has 2 tempura in field and one in hand then give an additional reward
        if(sgs.getPlayerSashimiAmount(playerId) == 2)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Sashimi))
            {
                value += 6.66;
            }
        }

        // give additional reward if dumplings in field and hand. Scales with number of dumplings in field
        if(sgs.getPlayerDumplingAmount(playerId) > 1)
        {
            if(playerHand.getComponents().stream().anyMatch(o -> o.type == SGCard.SGCardType.Dumpling))
            {
                value += sgs.getPlayerDumplingAmount(playerId) / 2.0;
            }
        }

        // calculate what position the current number of maki would give the player
        float currentMakiPos = sgs.getNPlayers();
        for(int id = 0; id < sgs.getNPlayers(); id++)
        {
            if(id != playerId)
            {
                if(getMakiCount(sgs, playerId, playerField) <= getMakiCount(sgs, id, sgs.getPlayerField(id)))
                {
                    currentMakiPos--;
                }
            }

        }

//        value += currentMakiPos * 3.0 * ((float)(sgs.cardAmount - sgs.playerHands.get(playerId).getComponents().size()) / (float)sgs.cardAmount);
//
        float currentPuddingPos = sgs.getNPlayers();
        for(int id = 0; id < sgs.getNPlayers(); id++)
        {
            if(id != playerId)
            {
                if(playerField.stream().filter(o -> o.type == SGCard.SGCardType.Pudding).count() <
                        sgs.getPlayerField(id).stream().filter(o -> o.type == SGCard.SGCardType.Pudding).count())
                {
                    currentPuddingPos--;
                }
            }

        }

        // give a small reward for having a pudding in the first 2 rounds
        if(sgs.getTurnOrder().getRoundCounter() < 2)
        {
            value += playerField.stream().filter(o -> o.type == SGCard.SGCardType.Pudding).count();
        }
        else
        {
            // Adjust the reward based on the current position in the pudding race
            if(currentPuddingPos == 1)
            {
                if(sgs.getNPlayers() > 2)
                {
                    value -= 6;
                }
            } else if (currentPuddingPos == sgs.getNPlayers())
            {
                value += 6;
            }
        }
        return value;
    }

    private double sigmoid(double x)
    {
        return 1 / (1 + Math.exp(-x));
    }

    private int getMakiCount(SGGameState sgs, int playerId, Deck<SGCard> playerField)
    {
        int makiPoints = 0;
        for (int j = 0; j < playerField.getSize(); j++) {
            switch (playerField.get(j).type) {
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
