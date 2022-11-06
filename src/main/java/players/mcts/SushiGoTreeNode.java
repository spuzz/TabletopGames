package players.mcts;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.interfaces.IStatisticLogger;
import core.turnorders.TurnOrder;
import games.sushigo.SGGameState;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;
import utilities.Utils;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.entropyOf;
import static utilities.Utils.noise;
class SortbyHeuristic implements Comparator<SushiGoTreeNode> {

    // Method
    // Sorting in ascending order of name
    public int compare(SushiGoTreeNode a, SushiGoTreeNode b)
    {
        return Double.compare(a.heuristicValue , b.heuristicValue);
    }
}
class SushiGoTreeNode {
    // Root node of tree
    SushiGoTreeNode root;
    // Parent of this node
    SushiGoTreeNode parent;
    // Children of this node
    Map<AbstractAction, SushiGoTreeNode> children = new HashMap<>();

    LinkedHashMap<AbstractAction, SushiGoTreeNode> childrenPruned = new  LinkedHashMap<>();

    private boolean isPruned = false;
    // Depth of this node
    final int depth;

    // Parameter for FPU
    double phi = 1;

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

    public double heuristicValue = 0.0;
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
    void mctsSearch(IStatisticLogger statsLogger) {

        // Variables for tracking time budget
        double avgTimeTaken = 0;
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
            SushiGoTreeNode selected = treePolicy(); // Replace here with FPUTreePolicy() to test it
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            if(selected.depth > player.maxDepth)
            {
                player.maxDepth = selected.depth;
            }
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
//                if(stop)
//                {
//                    System.out.println("Average time per Iteration " + (int)(avgTimeTaken * 1000));
//                }
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > player.params.budget;
            }
        }
        if (statsLogger != null) {
            logTreeStats(statsLogger, numIters, elapsedTimer.elapsedMillis(), avgTimeTaken);
        }
    }
    public void logTreeStats(IStatisticLogger statsLogger, int numIters, long timeTaken, double avgTimeTaken)
    {
        Map<String, Object> stats = new LinkedHashMap<>();
        statsLogger.record(stats);
        stats.put("round", state.getTurnOrder().getRoundCounter());
        stats.put("turn", state.getTurnOrder().getTurnCounter());
        stats.put("turnOwner", state.getTurnOrder().getTurnOwner());
        stats.put("iterations", numIters);
        stats.put("fmCalls", fmCallsCount);
        stats.put("time", timeTaken);
        stats.put("maxDepth", player.maxDepth);
        stats.put("nActionsRoot", children.size());
        AbstractAction bestAction = bestAction();
        stats.put("bestAction", bestAction);
        stats.put("averageTimePerIteration", avgTimeTaken);
        statsLogger.record(stats);
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

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - The urgency of each child of the node is computed (phi if it hasn't been visited before, UCB1-Tuned otherwise)
     * - The child with highest urgency is added to the tree.
     *
     * @return - new node added to the tree.
     */

    private SushiGoTreeNode FPUTreePolicy() {
        SushiGoTreeNode cur = this;
        while (cur.state.isNotTerminal() && cur.depth < player.params.maxTreeDepth) {
            // Node not fully extended
            if (!cur.unexpandedActions().isEmpty()) {
                // Iterate through actions and assign urgency to each action
                Map<AbstractAction, Double> urgency = new HashMap<>(); // urgency
                for (AbstractAction action : cur.children.keySet()) {
                    if (cur.children.get(action) == null) { // child never visited, assigned FPU
                        urgency.put(action, phi);
                    } else { // child visited, assigned UCB-Tuned
                        urgency.put(action, cur.children.get(action).ucb1t());
                    }
                }
                // Find most urgent action
                double u_value = -Double.MAX_VALUE; // most urgent value
                AbstractAction u_action = cur.children.keySet().iterator().next(); // most urgent action, initialized with first action
                for (AbstractAction action : urgency.keySet()) {
                    if (urgency.get(action) > u_value) {
                        u_value = urgency.get(action);
                        u_action = action;
                    }
                }

                if (u_value == phi) { // one of the unexpanded actions has the higher urgency value
                    cur = cur.expand();
                    return cur;
                } else { // one of the expanded actions has the highest urgency value
                    cur = cur.children.get(u_action); // u_action from this not from cur (cur assigned == null)
                    return cur;
                }
                // Node fully expanded
            } else {
                AbstractAction actionChosen = cur.ucb();
                cur = cur.children.get(actionChosen);
            }
        }
        return cur;
    }

    /**
     * @return - UCB1-Tuned value of the node
     */
    private double ucb1t(){

        double totReward = this.totValue;
        double nsa = this.nVisits + player.params.epsilon; // epsilon necessary?
        double n = this.parent.nVisits +1; // +1 bc counter has not been incremented yet (?)
        double qsa = totReward / (nsa); // average reward
        double explorationTerm = player.params.K* Math.sqrt(Math.log(n) / (nsa)); // C = Math.sqrt(2)
        double vsa = ((Math.pow(totReward,2)/nsa)-Math.pow((totReward/nsa),2))+Math.sqrt((2*Math.log(n))/nsa);

        // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
        boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
        double value = iAmMoving ? qsa : -qsa; // opponent modeling here
        value += explorationTerm*Math.min(1/4,vsa);

        return value;
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

        if(player.params.useProgressiveUnpruning)
        {
            // If this node has not been pruned and it N(S) > T then prune
            if(!isPruned && player.params.pup_T < nVisits) {
                int kNodes = player.params.pup_k_init;
                isPruned = true;

                // Sort the list by heuristic value to make it easier to prune the worst nodes
                Collection<SushiGoTreeNode> nodes = children.values();
                ArrayList<SushiGoTreeNode> list = new ArrayList<SushiGoTreeNode>(nodes);
                list.sort(new SortbyHeuristic());

                // Keep pruning until we reach k_init nodes left
                while (list.size() > kNodes) {
                    SushiGoTreeNode nodeToPrune = list.remove(0);
                    AbstractAction actionToRemove = children.keySet().stream().filter(o -> children.get(o) == nodeToPrune).findFirst().get();
                    // Add the pruned node to a list so it can be added back again later
                    childrenPruned.put(actionToRemove, children.get(actionToRemove));
                    children.remove(actionToRemove);
                }
            }

            // if we have already pruned and there are some nodes left to be added back, check if we should add them
            if(isPruned && childrenPruned.size() > 0)
            {
                // formula for re adding a node (A*B^k_nodes)
                double progressiveUnprune = player.params.pup_A * player.params.pup_B;
                int kNodes = player.params.pup_k_init;
                int curPower = 2;
                while (progressiveUnprune < nVisits) {
                    kNodes++;
                    progressiveUnprune = player.params.pup_A * (Math.pow(player.params.pup_B, curPower));
                    curPower++;
                }
                // add back the required nodes
                while(children.size() < kNodes)
                {
                    children.put(childrenPruned.entrySet().iterator().next().getKey(),childrenPruned.entrySet().iterator().next().getValue() );
                    childrenPruned.remove(childrenPruned.entrySet().iterator().next().getKey());
                }
            }
        }



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

            ;
            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? childValue : -childValue;
            uctValue += explorationTerm;

            if(player.params.useProgressiveBias)
            {
                // Calculate how good an action is using the sushigoheuristic
                child.heuristicValue =  player.params.getHeuristic().evaluateState(sgs,player.getPlayerID());
                // Degrade value after each visit
                double progressiveBias = child.heuristicValue/ (child.nVisits);
                // if this a min turn then reverse the score
                uctValue += iAmMoving ? progressiveBias : -progressiveBias;
            }


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

        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        int curRound = state.getTurnOrder().getRoundCounter();
        if (player.params.rolloutLength > 0 ) {
            while (!finishRollout(rolloutState, rolloutDepth, curRound)) {
                curRound = rolloutState.getTurnOrder().getRoundCounter();
                List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState);
                AbstractAction next;
                if(player.params.useRolloutBias)
                {
                    // calculate heuristic value for every possible action in this state
                    List<Double> actionValues = evaluateActions(player, rolloutState, availableActions);

                    // get the minimum score
                    double minValue = Collections.min(actionValues) - 0.0000001;

                    // lower all scores by the lowest amount to give a bigger range
                    actionValues.replaceAll(aDouble -> aDouble - minValue);

                    // get sum of all values
                    double actionValueTotal = actionValues.stream().reduce(0.0, Double::sum);

                    // Pick a random number between 0 and total sum and use to pick a value in that range
                    // This will give higher rewards a higher probability of being selected
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
                    next = availableActions.get(actionIndex);
                }
                else {
                    next = randomPlayer.getAction(rolloutState, availableActions);
                }

                advance(rolloutState, next);

                rolloutDepth++;
            }
        }

        // Evaluate final state and return normalised score
        return player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
    }

    /**
        Evaluate each available action in the state by using the forward model with each action
        Then using the sushigo heuristic to estimate the quality of the action
     **/
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
