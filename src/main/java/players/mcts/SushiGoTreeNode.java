package players.mcts;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.sushigo.SGGameState;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class SushiGoTreeNode {
    // Root node of tree
    SushiGoTreeNode root;
    // Parent of this node
    SushiGoTreeNode parent;
    // Children of this node
    Map<AbstractAction, SushiGoTreeNode> children = new HashMap<>();
    // Depth of this node
    final int depth;

    // Total value of this node
    private double totValue;
    // Number of visits
    private int nVisits;
    // Number of FM calls and State copies up until this node
    private int fmCallsCount;
    // Parameters guiding the search
    private SushiGoMCTSPlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    // State in this node (closed loop)
    private AbstractGameState state;

    protected SushiGoTreeNode(SushiGoMCTSPlayer player, SushiGoTreeNode parent, AbstractGameState state, Random rnd) {
        this.player = player;
        this.fmCallsCount = 0;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        totValue = 0.0;
        setState(state);
        if (parent != null) {
            depth = parent.depth + 1;
        } else {
            depth = 0;
        }
        this.rnd = rnd;
    }

    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void mctsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(player.params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            SushiGoTreeNode selected = treePolicy();
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition
            PlayerConstants budgetType = player.params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
                if(stop == true)
                {
                    System.out.println(numIters);
                }
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > player.params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private SushiGoTreeNode treePolicy() {

        SushiGoTreeNode cur = this;
        SushiGoTreeNode first = this;
        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal() && cur.depth < player.params.maxTreeDepth
                && cur.state.getTurnOrder().getRoundCounter() == first.state.getTurnOrder().getRoundCounter()){
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand();
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                cur = cur.children.get(actionChosen);
            }
        }

        return cur;
    }


    private void setState(AbstractGameState newState) {
        state = newState;
        for (AbstractAction action : player.getForwardModel().computeAvailableActions(state)) {
            children.put(action, null); // mark a new node to be expanded
        }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */
    private SushiGoTreeNode expand() {
        // Find random child not already created
        Random r = new Random(player.params.getRandomSeed());
        // pick a random unchosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node
        SushiGoTreeNode tn = new SushiGoTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        for (AbstractAction action : children.keySet()) {
            SushiGoTreeNode child = children.get(action);
            if (child == null)
                throw new AssertionError("Should not be here");

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + player.params.epsilon);

            // default to standard UCB
            double explorationTerm = player.params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + player.params.epsilon));
            // unless we are using a variant
            SGGameState sgs = (SGGameState)state.copy();
            advance(sgs, action);
            double progressiveBias = player.params.getHeuristic().evaluateState(sgs,state.getCurrentPlayer())/ (1 + child.nVisits);
            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? childValue : -childValue;
            uctValue += explorationTerm;
            uctValue += iAmMoving ? progressiveBias : -progressiveBias;

            // Apply small noise to break ties randomly
            uctValue = noise(uctValue, player.params.epsilon, player.rnd.nextDouble());

            // Assign value
            if (uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        root.fmCallsCount++;  // log one iteration complete
        return bestAction;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut() {
        int rolloutDepth = 0; // counting from end of tree

        boolean chopsticks = false;

        if(((SGGameState)state).getPlayerChopSticksActivated(player.getPlayerID()) == true && state.getCurrentPlayer() == 0)
        {
            //schopsticks = true;
        }
        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        int curRound = state.getTurnOrder().getRoundCounter();
        if (player.params.rolloutLength > 0 ) {
            while (!finishRollout(rolloutState, rolloutDepth, curRound)) {
                curRound = rolloutState.getTurnOrder().getRoundCounter();
                List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState);
                List<Double> actionValues = evaluateActions(player, rolloutState, availableActions);
                double minValue = Collections.min(actionValues) - 0.0000001;
                for(int a=0; a<actionValues.size(); a++)
                {
                    actionValues.set(a, actionValues.get(a) - minValue);
                }
                double actionValueTotal = actionValues.stream().reduce(0.0, Double::sum);

                Random randomNumber = new Random();
                double pick = randomNumber.nextDouble() * actionValueTotal;
                double current = 0;
                int actionIndex = 0;
                for(int y=0; y < actionValues.size(); y++){
                    current += actionValues.get(y);
                    if(pick <= current){
                        actionIndex = y;
                        break;
                    }
                }
                AbstractAction next = availableActions.get(actionIndex);
                //AbstractAction next = randomPlayer.getAction(rolloutState, availableActions);
                if(chopsticks == true)
                {
                    System.out.println("------------------------------------------------------------------------");
                    System.out.println(rolloutState.getCurrentPlayer());
                    System.out.println(availableActions);
                    System.out.println(actionValues);
                    System.out.println(next);
                }
                advance(rolloutState, next);

                rolloutDepth++;
            }
        }

        // Evaluate final state and return normalised score
        return player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
    }

    private List<Double> evaluateActions(SushiGoMCTSPlayer player, AbstractGameState rolloutState, List<AbstractAction> availableActions) {
        List<Double> values = new ArrayList<Double>();
        for(AbstractAction action : availableActions)
        {
            AbstractGameState actionState = rolloutState.copy();
            player.getForwardModel().next(actionState, action);
            values.add(player.params.getHeuristic().evaluateState(actionState, rolloutState.getCurrentPlayer()));
        }

        return values;
    }
    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth, int curRound) {

        if ((depth >= player.params.rolloutLength || rollerState.getTurnOrder().getRoundCounter() != curRound) &&
                ( rollerState.getTurnOrder().getFirstPlayer() == rollerState.getCurrentPlayer()))
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        SushiGoTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            n = n.parent;
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (AbstractAction action : children.keySet()) {
            if (children.get(action) != null) {
                SushiGoTreeNode node = children.get(action);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                // Save best value (highest visit count)
                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

}
