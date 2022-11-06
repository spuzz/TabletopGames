package players.mcts;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static players.mcts.MCTSEnums.OpponentTreePolicy.Paranoid;
import static players.mcts.MCTSEnums.SelectionPolicy.ROBUST;
import static players.mcts.MCTSEnums.Strategies.RANDOM;
import static players.mcts.MCTSEnums.TreePolicy.UCB;

/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses SushiGoTreeNode in place of
 * SingleTreeNode.
 */
public class SushiGoMCTSPlayer extends AbstractPlayer {

    Random rnd;
    SushiGoMCTSParams params;

    public int maxDepth = 0;
    public SushiGoMCTSPlayer() {
        this(System.currentTimeMillis());
    }

    public SushiGoMCTSPlayer(long seed) {
        this.params = new SushiGoMCTSParams(seed);
        rnd = new Random(seed);
        setName("Sushi Go MCTS");

        // These parameters can be changed, and will impact the Basic MCTS algorithm
        this.params.K = Math.sqrt(2);
        this.params.rolloutLength = 10;
        this.params.maxTreeDepth = 5;
        this.params.epsilon = 1e-6;

        // These parameters are ignored by BasicMCTS - if you want to play with these, you'll
        // need to upgrade to MCTSPlayer
        this.params.information = MCTSEnums.Information.Closed_Loop;
        this.params.rolloutType = RANDOM;
        this.params.selectionPolicy = ROBUST;
        this.params.opponentTreePolicy = Paranoid;
        this.params.treePolicy = UCB;
    }

    public SushiGoMCTSPlayer(SushiGoMCTSParams params) {
        this.params = params;
        rnd = new Random(params.getRandomSeed());
        setName("SushiGo MCTS");
    }



    @Override
    public AbstractAction getAction(AbstractGameState gameState, List<AbstractAction> allActions) {
        maxDepth = 0;
        // Search for best action from the root
        SushiGoTreeNode root = new SushiGoTreeNode(this, null, gameState, rnd);

        // mctsSearch does all of the hard work
        root.mctsSearch(statsLogger);

        //System.out.println("Max Depth Reached " + statsLogger.);

        // Return best action
        return root.bestAction();
    }

    public void setStateHeuristic(IStateHeuristic heuristic) {
        this.params.heuristic = heuristic;
    }


    @Override
    public String toString() {
        return "SushiGoMCTS";
    }

    @Override
    public SushiGoMCTSPlayer copy() {
        return this;
    }
}