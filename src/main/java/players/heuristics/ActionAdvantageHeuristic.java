package players.heuristics;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.function.ToDoubleBiFunction;

import static java.util.stream.Collectors.toList;

public class ActionAdvantageHeuristic extends AbstractPlayer implements ToDoubleBiFunction<AbstractAction, AbstractGameState> {

    Random rnd = new Random(System.currentTimeMillis());

    String filename;

    protected double RND_WEIGHT;

    Map<Integer, Double> actionAdvantage = new HashMap<>();

    public ActionAdvantageHeuristic(String filename) {
        this.filename = filename;
        initialiseFromFile();
    }

    @Override
    public void initializePlayer(AbstractGameState state) {
        initialiseFromFile();
    }

    private void initialiseFromFile() {

        try {
            if (filename != null && (new File(filename)).exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(filename));
                String weight = reader.readLine();
                RND_WEIGHT = Double.parseDouble(weight);
                reader.readLine();
                // we expect two columns; hash and advantage estimate

                //   List<List<Double>> data = new ArrayList<>();
                String nextLine = reader.readLine();
                while (nextLine != null) {
                    List<Double> data = Arrays.stream(nextLine.split(",")).map(Double::valueOf).collect(toList());

                    int hash = data.get(0).intValue();
                    double advantage = data.get(1);
                    actionAdvantage.put(hash, advantage);
                    nextLine = reader.readLine();
                }

                reader.close();
            } else {
                System.out.println("File not found : " + filename);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ActionAdvantageHeuristic(Map<Integer, Double> advantages, double rndWeight) {
        actionAdvantage = advantages;
        RND_WEIGHT = rndWeight;
    }

    @Override
    public AbstractAction getAction(AbstractGameState gameState, List<AbstractAction> possibleActions) {

        double bestValue = Double.NEGATIVE_INFINITY;
        AbstractAction retValue = possibleActions.get(0);
        for (AbstractAction action : possibleActions) {
            double actionValue = actionAdvantage.getOrDefault(action.hashCode(), 0.0) + rnd.nextDouble() * RND_WEIGHT;
            if (actionValue > bestValue) {
                retValue = action;
                bestValue = actionValue;
            }
        }
        return retValue;
    }

    Set<Integer> unknownHashCodes = new HashSet<>();

    @Override
    public double applyAsDouble(AbstractAction abstractAction, AbstractGameState gameState) {
        int hash = abstractAction.hashCode();
        if (!actionAdvantage.isEmpty() && !actionAdvantage.containsKey(hash) && !unknownHashCodes.contains(hash)) {
            unknownHashCodes.add(hash);
            System.out.println("Action not found : " + hash + " " + abstractAction + " " + abstractAction.toString());
        }
        return actionAdvantage.getOrDefault(abstractAction.hashCode(), 0.0);
    }
}
