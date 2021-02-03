package games.catan;

import core.AbstractParameters;

import java.util.HashMap;

public class CatanParameters extends AbstractParameters {
    private String dataPath;
    public final int n_actions_per_turn = 1;

    public final int n_players= 4;

    public final int n_settlements = 5;
    public final int n_cities = 4;
    public final int n_roads = 15;
    public final int n_resource_cards = 19;
    public final int n_tiles_per_row = 7;

    public final int max_negotiation_count = 3; // max number of attempts to renegotiate player trade
    public final int default_exchange_rate = 4; // trading with the bank 1:4 ratio by default
    public final int max_cards_without_discard = 7; // max number of resources a player may hold in hand without risking discarding

    // the minimum number of knights required to take the largest army
    public final int min_army_size = 3;
    public final int min_longest_road = 5;

    // points
    public final int settlement_value = 1;
    public final int city_value = 2;
    public final int longest_road_value = 2;
    public final int largest_army_value = 2;
    public final int victory_point_value = 1;
    public final int points_to_win = 10;

    public CatanParameters(long seed){
        super(seed);
    }

    public CatanParameters(String dataPath, long seed){
        super(seed);
        this.dataPath = dataPath;
    }

    public String getDataPath(){
        return dataPath;
    }

    @Override
    protected AbstractParameters _copy() {
        return null;
    }

    @Override
    protected boolean _equals(Object o) {
        return false;
    }

    HashMap<TileType, Integer> tileCounts = new HashMap<CatanParameters.TileType, Integer>() {{
        put(CatanParameters.TileType.HILLS, 3);
        put(CatanParameters.TileType.FOREST, 4);
        put(CatanParameters.TileType.MOUNTAINS, 3);
        put(CatanParameters.TileType.FIELDS, 4);
        put(CatanParameters.TileType.PASTURE, 4);
        put(CatanParameters.TileType.DESERT, 1);
    }};


    // todo that could be simplified
    // [2, 12]    x 1
    // [3,...,11] x 2
    HashMap<Integer, Integer> numberTokens = new HashMap<Integer, Integer>(){{
        put(2, 1);
        put(3, 2);
        put(4, 2);
        put(5, 2);
        put(6, 2);
        put(8, 2);
        put(9, 2);
        put(10, 2);
        put(11, 2);
        put(12, 1);
    }};

    public enum TileType {
        HILLS,
        FOREST,
        MOUNTAINS,
        FIELDS,
        PASTURE,
        DESERT,
        SEA
    }

    public enum Resources {
        BRICK,
        LUMBER,
        ORE,
        GRAIN,
        WOOL
    }

    public enum HarborTypes {
        BRICK,
        LUMBER,
        ORE,
        GRAIN,
        WOOL,
        GENERIC
    }

    public enum CardTypes{
        KNIGHT_CARD,
        PROGRESS_CARD,
        VICTORY_POINT_CARD
    }

    /* Mapping of which field produces what*/
    public static HashMap<TileType, Resources> productMapping = new HashMap<TileType, Resources>(){{
        put(TileType.HILLS, Resources.BRICK);
        put(TileType.FOREST, Resources.LUMBER);
        put(TileType.MOUNTAINS, Resources.ORE);
        put(TileType.FIELDS, Resources.GRAIN);
        put(TileType.PASTURE, Resources.WOOL);
        put(TileType.DESERT, null);
        put(TileType.SEA, null);
    }};

    /* Mapping from name to price of item (cost is in the same order as Resources) */
    public static HashMap<String, int[]> costMapping = new HashMap<String, int[]>(){{
        // cost order: Brick, lumber, ore, grain, wool
        put("settlement", new int[]{1, 1, 0, 1, 1});
        put("city", new int[]{0, 0, 3, 0, 2});
        put("road", new int[]{1, 1, 0, 0, 0});
        put("developmentCard", new int[]{0, 0, 1, 1, 1});
    }};

    HashMap<String, Integer> tokenCounts = new HashMap<String, Integer>() {{
        put("settlement", 5);
        put("city", 4);
        put("road", 15);
    }};

    HashMap<String, Integer> developmentCardCount = new HashMap<String, Integer>() {{
        put("Knight", 14);
        put("Monopoly", 2);
        put("Year of Plenty", 2);
        put("Road Building", 2);
        put("Victory Point", 5);
    }};

    public static HashMap<HarborTypes, Integer> harborCount = new HashMap<HarborTypes, Integer>() {{
        put(HarborTypes.BRICK, 1);
        put(HarborTypes.LUMBER, 1);
        put(HarborTypes.ORE, 1);
        put(HarborTypes.GRAIN, 1);
        put(HarborTypes.WOOL, 1);
        put(HarborTypes.GENERIC, 4);
    }};
}
