package evaluation;

import core.AbstractParameters;
import core.AbstractPlayer;
import core.ParameterFactory;
import core.interfaces.IGameListener;
import core.interfaces.IStatisticLogger;
import games.GameType;
import groupL.SushiGoHeuristic;
import groupL.*;
import players.PlayerConstants;
import players.PlayerFactory;
import players.mcts.*;
import utilities.FileStatsLogger;

import java.util.*;

import static utilities.Utils.GameResult;
import static utilities.Utils.getArg;


public class RoundRobinTournament extends AbstractTournament {
    private static boolean debug = false;
    public final boolean selfPlay;
    private final int gamesPerMatchUp;
    protected List<IGameListener> listeners;
    int[] pointsPerPlayer;
    LinkedList<Integer> agentIDs;
    private int matchUpsRun;
    public boolean verbose = true;

    /**
     * Create a round robin tournament, which plays all agents against all others.
     *
     * @param agents          - players for the tournament.
     * @param gameToPlay      - game to play in this tournament.
     * @param playersPerGame  - number of players per game.
     * @param gamesPerMatchUp - number of games for each combination of players.
     * @param selfPlay        - true if agents are allowed to play copies of themselves.
     */
    public RoundRobinTournament(List<AbstractPlayer> agents, GameType gameToPlay, int playersPerGame,
                                int gamesPerMatchUp, boolean selfPlay, AbstractParameters gameParams) {
        super(agents, gameToPlay, playersPerGame, gameParams);
        if (!selfPlay && playersPerGame > this.agents.size()) {
            throw new IllegalArgumentException("Not enough agents to fill a match without self-play." +
                    "Either add more agents, reduce the number of players per game, or allow self-play.");
        }

        this.agentIDs = new LinkedList<>();
        for (int i = 0; i < this.agents.size(); i++)
            this.agentIDs.add(i);

        this.gamesPerMatchUp = gamesPerMatchUp;
        this.selfPlay = selfPlay;
        this.pointsPerPlayer = new int[agents.size()];
    }

    /**
     * Main function, creates an runs the tournament with the given settings and players.
     */
    @SuppressWarnings({"UnnecessaryLocalVariable", "ConstantConditions"})
    public static void main(String[] args) {
        List<String> argsList = Arrays.asList(args);
        if (argsList.contains("--help") || argsList.contains("-h")) {
            System.out.println(
                    "There are a number of possible arguments:\n" +
                            "\tgame=          The name of the game to play. Defaults to SushiGo.\n" +
                            "\tnPlayers=      The number of players in each game. Defaults to 2.\n" +
                            "\tplayers=       The directory containing agent JSON files for the competing Players\n" +
                            "\t               If not specified, this defaults to very basic OSLA, RND, RHEA and MCTS players.\n" +
                            "\tgameParams=    (Optional) A JSON file from which the game parameters will be initialised.\n" +
                            "\tselfPlay=      If true, then multiple copies of the same agent can be in one game.\n" +
                            "\t               Defaults to false\n" +
                            "\tmode=          exhaustive|random - defaults to exhaustive.\n" +
                            "\t               exhaustive will iterate exhaustively through every possible matchup: \n" +
                            "\t               every possible player in every possible position. This can be excessive\n" +
                            "\t               for a large number of players, and random will have a random matchup \n" +
                            "\t               in each game, while ensuring no duplicates, and that all players get the\n" +
                            "\t               the same number of games in total.\n" +
                            "\tmatchups=      The total number of matchups to run if mode=random...\n" +
                            "\t               ...or the number of matchups to run per combination of players if mode=exhaustive\n" +
                            "\tlistener=      (Optional) The full class name of an IGameListener implementation. \n" +
                            "\t               Defaults to utilities.GameResultListener. \n" +
                            "\t               A pipe-delimited string can be provided to gather many types of statistics \n" +
                            "\t               from the same set of games.\n" +
                            "\tlistenerFile= (Optional) Will be used as the IStatisticsLogger log file.\n" +
                            "\t               Defaults to RoundRobinReport.txt\n" +
                            "\t               A pipe-delimited list should be provided if each distinct listener should\n" +
                            "\t               use a different log file.\n" +
                            "\tstatsLog=      The file to use for logging agent-specific statistics (e.g. MCTS iterations/depth)\n" +
                            "\t               A single line will be generated as the average for each agent, implicitly assuming they are\n" +
                            "\t               all of the same type. If not supplied, then no logging will take place.\n"
            );
            return;
        }
        /* 1. Settings for the tournament */
        GameType gameToPlay = GameType.valueOf(getArg(args, "game", "SushiGo"));
        int nPlayersPerGame = getArg(args, "nPlayers", 2);
        boolean selfPlay = getArg(args, "selfPlay", false);
        String mode = getArg(args, "mode", "exhaustive");
        int matchups = getArg(args, "matchups", 1);
        String playerDirectory = getArg(args, "players", "");
        String gameParams = getArg(args, "gameParams", "");
        String statsLogPrefix = getArg(args, "statsLog", "sushiGo");

        List<String> listenerClasses = new ArrayList<>(Arrays.asList(getArg(args, "listener", "utilities.GameResultListener").split("\\|")));
        List<String> listenerFiles = new ArrayList<>(Arrays.asList(getArg(args, "listenerFile", "RoundRobinReport.txt").split("\\|")));

        if (listenerClasses.size() > 1 && listenerFiles.size() > 1 && listenerClasses.size() != listenerFiles.size())
            throw new IllegalArgumentException("Lists of log files and listeners must be the same length");

        LinkedList<AbstractPlayer> agents = new LinkedList<>();
        if (!playerDirectory.equals("")) {
            agents.addAll(PlayerFactory.createPlayers(playerDirectory));
            if (!statsLogPrefix.equals("")) {
                for (AbstractPlayer agent : agents) {
                    IStatisticLogger logger = IStatisticLogger.createLogger("utilities.SummaryLogger", statsLogPrefix + "_" + agent.toString() + ".txt");
                    logger.record("Name", agent.toString());
                    agent.setStatsLogger(logger);
                }
            }
        } else {
            /* 2. Set up players */
            MCTSParams params = new MCTSParams();
            params.maxTreeDepth = 10;
            params.rolloutLength = 21;
            params.budget = 1000;
            params.budgetType = PlayerConstants.BUDGET_TIME;
            BasicMCTSPlayer basicMcts = new BasicMCTSPlayer(params);
            IStatisticLogger logger = IStatisticLogger.createLogger("utilities.SummaryLogger", statsLogPrefix + "_" + basicMcts.toString() + ".txt");
            logger.record("Name", basicMcts.toString());
            basicMcts.setStatsLogger(logger);

            SushiGoMCTSParams sushiParams = new SushiGoMCTSParams();
            sushiParams.useProgressiveBias = true;
            sushiParams.useProgressiveUnpruning = true;
            sushiParams.pup_k_init = 3;
            sushiParams.pup_T = 20;
            sushiParams.pup_B = 2.0;
            sushiParams.pup_A = 50;
            //sushiParams.useRolloutBias = true;
            sushiParams.maxTreeDepth = 20;
            sushiParams.rolloutLength = 21;
            sushiParams.budgetType = PlayerConstants.BUDGET_TIME;
            sushiParams.budget = 1000;
            sushiParams.heuristic = new SushiGoHeuristic();

            SushiGoMCTSPlayer sushiGoMCTSPlayer = new SushiGoMCTSPlayer(sushiParams);
            IStatisticLogger basicMCTSlogger = IStatisticLogger.createLogger("utilities.SummaryLogger", statsLogPrefix + "_" + sushiGoMCTSPlayer.toString() + ".txt");
            basicMCTSlogger.record("Name", sushiGoMCTSPlayer.toString());
            sushiGoMCTSPlayer.setStatsLogger(basicMCTSlogger);

            //agents.add(new RandomPlayer());
            //agents.add(new RMHCPlayer());
            //agents.add(new OSLAPlayer());

            SushiGoMCTSParams sushiParams2 = new SushiGoMCTSParams();
            //sushiParams2.useProgressiveBias = true;
            //sushiParams2.useProgressiveUnpruning = true;
            //sushiParams2.useRolloutBias = true;
            sushiParams2.maxTreeDepth = 20;
            sushiParams2.rolloutLength = 21;
            sushiParams2.budgetType = PlayerConstants.BUDGET_TIME;
            sushiParams2.budget = 1000;
            sushiParams2.heuristic = new SushiGoHeuristic();

            SushiGoMCTSPlayer sushiGoMCTSPlayer2 = new SushiGoMCTSPlayer(sushiParams2);
            IStatisticLogger logger2 = IStatisticLogger.createLogger("utilities.SummaryLogger", statsLogPrefix + "_" + sushiGoMCTSPlayer2.toString() + ".txt");
            logger2.record("Name", sushiGoMCTSPlayer2.toString() + "2");
            sushiGoMCTSPlayer2.setStatsLogger(logger2);


            agents.add(sushiGoMCTSPlayer);
            //agents.add(sushiGoMCTSPlayer2);
            agents.add(basicMcts);
        }

        AbstractParameters params = ParameterFactory.createFromFile(gameToPlay, gameParams);

        // Run!
        RoundRobinTournament tournament = mode.equals("exhaustive") ?
                new RoundRobinTournament(agents, gameToPlay, nPlayersPerGame, matchups, selfPlay, params) :
                new RandomRRTournament(agents, gameToPlay, nPlayersPerGame, selfPlay, matchups,
                        System.currentTimeMillis(), params);

        tournament.listeners = new ArrayList<>();
        for (int l = 0; l < listenerClasses.size(); l++) {
            IStatisticLogger logger = new FileStatsLogger(listenerFiles.get(l));
            IGameListener gameTracker = IGameListener.createListener(listenerClasses.get(l), logger);
            tournament.listeners.add(gameTracker);
        }
        tournament.runTournament();
        if (!statsLogPrefix.equals("")) {
            for (int i = 0; i < agents.size(); i++) {
                AbstractPlayer agent = agents.get(i);
                agent.getStatsLogger().record("WinRate", tournament.pointsPerPlayer[i] / (double) (tournament.matchUpsRun * tournament.gamesPerMatchUp));
                System.out.println("Statistics for agent " + i + " " + agent);
                agent.getStatsLogger().processDataAndFinish();
            }
        }
    }

    /**
     * Runs the round robin tournament.
     */
    @Override
    public void runTournament() {
        for (int g = 0; g < games.size(); g++) {
            if (verbose)
                System.out.println("Playing " + games.get(g).getGameType().name());

            LinkedList<Integer> matchUp = new LinkedList<>();
            createAndRunMatchUp(matchUp, g);
            int gameCounter = (gamesPerMatchUp * matchUpsRun);
            int gamesPerPlayer = gameCounter * playersPerGame.get(g) / agents.size();

            if (verbose)
                for (int i = 0; i < this.agents.size(); i++) {
                    System.out.printf("%s got %d points %n", agents.get(i).getPlayerID(), pointsPerPlayer[i]);
                    System.out.printf("%s won %.1f%% of the %d games of the tournament. %n",
                            agents.get(i), 100.0 * pointsPerPlayer[i] / gameCounter, gameCounter);
                    System.out.printf("%s won %.1f%% of the games it played during the tournament. %n",
                            agents.get(i), 100.0 * pointsPerPlayer[i] / gamesPerPlayer);
                }
        }
        for (IGameListener listener : listeners)
            listener.allGamesFinished();
    }

    /**
     * Recursively creates one combination of players and evaluates it.
     *
     * @param matchUp - current combination of players, updated recursively.
     * @param gameIdx - index of game to play with this match-up.
     */
    public void createAndRunMatchUp(LinkedList<Integer> matchUp, int gameIdx) {
        if (matchUp.size() == playersPerGame.get(gameIdx)) {
            evaluateMatchUp(matchUp, gameIdx);
        } else {
            for (Integer agentID : this.agentIDs) {
                if (selfPlay || !matchUp.contains(agentID)) {
                    matchUp.add(agentID);
                    createAndRunMatchUp(matchUp, gameIdx);
                    matchUp.remove(agentID);
                }
            }
        }
    }

    /**
     * Evaluates one combination of players.
     *
     * @param agentIDs - IDs of agents participating in this run.
     * @param gameIdx  - index of game to play in this evaluation.
     */
    protected void evaluateMatchUp(List<Integer> agentIDs, int gameIdx) {
        if (debug)
            System.out.printf("Evaluate %s at %tT%n", agentIDs.toString(), System.currentTimeMillis());
        LinkedList<AbstractPlayer> matchUpPlayers = new LinkedList<>();
        for (int agentID : agentIDs)
            matchUpPlayers.add(this.agents.get(agentID));

        for (IGameListener listener : listeners) {
            games.get(gameIdx).addListener(listener);
        }

        // Run the game N = gamesPerMatchUp times with these players
        long currentSeed = games.get(gameIdx).getGameState().getGameParameters().getRandomSeed();
        for (int i = 0; i < this.gamesPerMatchUp; i++) {
            games.get(gameIdx).reset(matchUpPlayers, currentSeed + i + 1);

            games.get(gameIdx).run();  // Always running tournaments without visuals

            GameResult[] results = games.get(gameIdx).getGameState().getPlayerResults();
            for (int j = 0; j < matchUpPlayers.size(); j++) {
                pointsPerPlayer[agentIDs.get(j)] += results[j] == GameResult.WIN ? 1 : 0;
            }
        }
        games.get(gameIdx).clearListeners();
        matchUpsRun++;
    }
}
