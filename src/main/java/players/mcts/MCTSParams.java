package players.mcts;

import core.AbstractGameState;
import core.AbstractParameters;
import core.AbstractPlayer;
import core.ParameterFactory;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import core.interfaces.ITunableParameters;
import evaluation.TunableParameters;
import org.json.simple.JSONObject;
import players.PlayerParameters;
import players.simple.RandomPlayer;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Random;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Pattern;

import static players.mcts.MCTSEnums.Information.*;
import static players.mcts.MCTSEnums.MASTType.Rollout;
import static players.mcts.MCTSEnums.OpponentTreePolicy.MaxN;
import static players.mcts.MCTSEnums.OpponentTreePolicy.Paranoid;
import static players.mcts.MCTSEnums.SelectionPolicy.ROBUST;
import static players.mcts.MCTSEnums.Strategies.RANDOM;
import static players.mcts.MCTSEnums.TreePolicy.UCB;

public class MCTSParams extends PlayerParameters {

    public double K = Math.sqrt(2);
    public int rolloutLength = 10;
    public int maxTreeDepth = 10;
    public double epsilon = 1e-6;
    public MCTSEnums.Information information = Open_Loop;
    public MCTSEnums.MASTType MAST = Rollout;
    public boolean useMAST = false;
    public double MASTGamma = 0.0;
    public double MASTBoltzmann = 0.0;
    public MCTSEnums.Strategies expansionPolicy = RANDOM;
    public MCTSEnums.Strategies rolloutType = RANDOM;
    public MCTSEnums.Strategies oppModelType = RANDOM;
    public MCTSEnums.SelectionPolicy selectionPolicy = ROBUST;
    public MCTSEnums.TreePolicy treePolicy = UCB;
    public MCTSEnums.OpponentTreePolicy opponentTreePolicy = Paranoid;
    public String rolloutClass, oppModelClass = "";
    public double exploreEpsilon = 0.1;
    private IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;
    public boolean gatherExpertIterationData = false;
    public String expertIterationFileStem = "ExpertIterationData";
    public String advantageFunction = "";
    public int biasVisits = 0;
    public double progressiveWideningConstant = 0.0; //  Zero indicates switched off (well, less than 1.0)
    public double progressiveWideningExponent = 0.0;
    public boolean normaliseRewards = true;

    public MCTSParams() {
        this(System.currentTimeMillis());
    }

    public MCTSParams(long seed) {
        super(seed);
        addTunableParameter("K", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("boltzmannTemp", 1.0);
        addTunableParameter("rolloutLength", 10, Arrays.asList(6, 8, 10, 12, 20));
        addTunableParameter("maxTreeDepth", 10, Arrays.asList(1, 3, 10, 30));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("rolloutType", MCTSEnums.Strategies.RANDOM);
        addTunableParameter("oppModelType", MCTSEnums.Strategies.RANDOM);
        addTunableParameter("information", Open_Loop, Arrays.asList(MCTSEnums.Information.values()));
        addTunableParameter("selectionPolicy", ROBUST, Arrays.asList(MCTSEnums.SelectionPolicy.values()));
        addTunableParameter("treePolicy", UCB);
        addTunableParameter("opponentTreePolicy", MaxN);
        addTunableParameter("exploreEpsilon", 0.1);
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
        addTunableParameter("expansionPolicy", MCTSEnums.Strategies.RANDOM);
        addTunableParameter("MAST", Rollout);
        addTunableParameter("MASTGamma", 0.0);
        addTunableParameter("rolloutClass", "");
        addTunableParameter("oppModelClass", "");
        addTunableParameter("expertIteration", false);
        addTunableParameter("expIterFile", "");
        addTunableParameter("advantageFunction", "");
        addTunableParameter("biasVisits", 0);
        addTunableParameter("progressiveWideningConstant", 0.0);
        addTunableParameter("progressiveWideningExponent", 0.0);
        addTunableParameter("normaliseRewards", true);
    }

    @Override
    public void _reset() {
        super._reset();
        useMAST = false;
        K = (double) getParameterValue("K");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
        rolloutType = (MCTSEnums.Strategies) getParameterValue("rolloutType");
        oppModelType = (MCTSEnums.Strategies) getParameterValue("oppModelType");
        information = (MCTSEnums.Information) getParameterValue("information");
        selectionPolicy = (MCTSEnums.SelectionPolicy) getParameterValue("selectionPolicy");
        MCTSEnums.Strategies expansionPolicy = (MCTSEnums.Strategies) getParameterValue("expansionPolicy");
        treePolicy = (MCTSEnums.TreePolicy) getParameterValue("treePolicy");
        opponentTreePolicy = (MCTSEnums.OpponentTreePolicy) getParameterValue("opponentTreePolicy");
        exploreEpsilon = (double) getParameterValue("exploreEpsilon");
        MASTBoltzmann = (double) getParameterValue("boltzmannTemp");
        MAST = (MCTSEnums.MASTType) getParameterValue("MAST");
        MASTGamma = (double) getParameterValue("MASTGamma");
        rolloutClass = (String) getParameterValue("rolloutClass");
        oppModelClass = (String) getParameterValue("oppModelClass");
        gatherExpertIterationData = (boolean) getParameterValue("expertIteration");
        expertIterationFileStem = (String) getParameterValue("expIterFile");
        advantageFunction = (String) getParameterValue("advantageFunction");
        biasVisits = (int) getParameterValue("biasVisits");
        progressiveWideningConstant = (double) getParameterValue("progressiveWideningConstant");
        progressiveWideningExponent = (double) getParameterValue("progressiveWideningExponent");
        normaliseRewards = (boolean) getParameterValue("normaliseRewards");
        if (expansionPolicy == MCTSEnums.Strategies.MAST || rolloutType == MCTSEnums.Strategies.MAST) {
            useMAST = true;
        }
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
        if (heuristic instanceof TunableParameters) {
            TunableParameters tunableHeuristic = (TunableParameters) heuristic;
            for (String name : tunableHeuristic.getParameterNames()) {
                tunableHeuristic.setParameterValue(name, this.getParameterValue("heuristic." + name));
            }
        }
    }

    /**
     * Any nested tunable parameter space is highly likely to be an IStateHeuristic
     * If it is, then we set this as the heuristic after the parent code in TunableParameters
     * has done the work to merge the search spaces together.
     *
     * @param json The raw JSON
     * @return The instantiated object
     */
    @Override
    public ITunableParameters registerChild(String nameSpace, JSONObject json) {
        // TODO: Cater for a tunable rollout policy, as well as a state heuristic
        ITunableParameters child = super.registerChild(nameSpace, json);
        if (child instanceof IStateHeuristic) {
            heuristic = (IStateHeuristic) child;
            setParameterValue("heuristic", child);
        }
        return child;
    }

    @Override
    protected AbstractParameters _copy() {
        return new MCTSParams(System.currentTimeMillis());
    }


    /**
     * @return Returns the AbstractPlayer policy that will take actions during an MCTS rollout.
     * This defaults to a Random player.
     */
    public AbstractPlayer getRolloutStrategy() {
        // TODO: Cater for the rollout class being itself Tunable
        return constructStrategy(rolloutType, rolloutClass);
    }

    private AbstractPlayer constructStrategy(MCTSEnums.Strategies type, String details) {
        switch (type) {
            case RANDOM:
                return new RandomPlayer(new Random(getRandomSeed()));
            case MAST:
                return new MASTPlayer(new Random(getRandomSeed()));
            case CLASS:
                String[] classAndParams = details.split(Pattern.quote("|"));
                if (classAndParams.length > 2)
                    throw new IllegalArgumentException("Only a single string parameter is currently supported");
                try {
                    Class<?> rollout = Class.forName(classAndParams[0]);
                    if (classAndParams.length == 1)
                        return (AbstractPlayer) rollout.getConstructor().newInstance();
                    return (AbstractPlayer) rollout.getConstructor(String.class).newInstance(classAndParams[1]);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            default:
                throw new AssertionError("Unknown strategy type : " + type);
        }
    }

    @SuppressWarnings("unchecked")
    public ToDoubleBiFunction<AbstractAction, AbstractGameState> getAdvantageFunction() {
        if (advantageFunction.isEmpty() || advantageFunction.equalsIgnoreCase("none"))
            return null;
        String[] classAndParams = advantageFunction.split(Pattern.quote("|"));
        if (classAndParams.length > 2)
            throw new IllegalArgumentException("Only a single string parameter is currently supported");
        try {
            Class<?> rollout = Class.forName(classAndParams[0]);
            if (classAndParams.length == 1)
                return (ToDoubleBiFunction<AbstractAction, AbstractGameState>) rollout.getConstructor().newInstance();
            Constructor<ToDoubleBiFunction<AbstractAction, AbstractGameState>> con = (Constructor<ToDoubleBiFunction<AbstractAction, AbstractGameState>>) rollout.getConstructor(String.class);
            return con.newInstance(classAndParams[1]);

        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new AssertionError("Not reachable");
    }

    public AbstractPlayer getOpponentModel() {
        return constructStrategy(oppModelType, oppModelClass);
    }

    public IStateHeuristic getHeuristic() {
        return heuristic;
    }

    @Override
    public MCTSPlayer instantiate() {
        return new MCTSPlayer(this);
    }

}
