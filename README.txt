Copy groupL folder into the src\main\java folder of the TAG framework

Add the following line at the top of any class creating the agent:
import groupL.*;
import players.PlayerConstants;

Insert the following lines of code to construct the agent

SushiGoMCTSParams sushiParams = new SushiGoMCTSParams();
sushiParams.useProgressiveBias = true;
sushiParams.useProgressiveUnpruning = true;
sushiParams.pup_k_init = 3;
sushiParams.pup_T = 20;
sushiParams.pup_B = 2.0;
sushiParams.pup_A = 50;
sushiParams.maxTreeDepth = 20;
sushiParams.rolloutLength = 21;
sushiParams.breakMS = 20;
sushiParams.budgetType = PlayerConstants.BUDGET_TIME;
sushiParams.budget = 1000;
sushiParams.heuristic = new SushiGoHeuristic();

SushiGoMCTSPlayer sushiGoMCTSPlayer = new SushiGoMCTSPlayer(sushiParams);
agents.add(sushiGoMCTSPlayer);