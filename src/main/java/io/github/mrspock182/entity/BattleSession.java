package io.github.mrspock182.entity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class BattleSession {

    public final String player1;
    public final String player2;
    public final String player1UserId;
    public final String player2UserId;

    private final JSONArray team1;
    private final JSONArray team2;

    private final AtomicInteger score1   = new AtomicInteger(0);
    private final AtomicInteger score2   = new AtomicInteger(0);
    private final AtomicInteger roundNum = new AtomicInteger(0);
    private final Random        random   = new Random();

    public BattleSession(String player1, String player2,
                         String player1UserId, String player2UserId,
                         JSONArray team1, JSONArray team2) {
        this.player1       = player1;
        this.player2       = player2;
        this.player1UserId = player1UserId;
        this.player2UserId = player2UserId;
        this.team1         = team1;
        this.team2         = team2;
    }

    public JSONObject playRound() {
        int round = roundNum.incrementAndGet();
        int index = (round - 1) % team1.length();

        JSONObject pokemon1  = team1.getJSONObject(index);
        JSONObject pokemon2  = team2.getJSONObject(index);

        JSONObject ability1  = randomAbility(pokemon1);
        JSONObject ability2  = randomAbility(pokemon2);

        String name1   = ability1.optString("name", "unknown");
        String name2   = ability2.optString("name", "unknown");
        int    value1  = ability1.optInt("strength", 0);
        int    value2  = ability2.optInt("strength", 0);

        boolean draw   = value1 == value2;
        boolean p1Wins = !draw && value1 > value2;

        if (!draw) {
            if (p1Wins) score1.incrementAndGet();
            else        score2.incrementAndGet();
        }

        return new JSONObject()
                .put("round", round)
                .put("draw",  draw)
                .put("player1", new JSONObject()
                        .put("username",  player1)
                        .put("pokemon",   pokemonName(pokemon1))
                        .put("attribute", name1)
                        .put("strength",  value1)
                        .put("won",       p1Wins))
                .put("player2", new JSONObject()
                        .put("username",  player2)
                        .put("pokemon",   pokemonName(pokemon2))
                        .put("attribute", name2)
                        .put("strength",  value2)
                        .put("won",       !p1Wins && !draw))
                .put("scores", new JSONObject()
                        .put(player1, score1.get())
                        .put(player2, score2.get()));
    }

    // Picks a random ability from the pokemon's "abilities" array.
    // Falls back to a zeroed placeholder if the array is absent or empty.
    private JSONObject randomAbility(JSONObject pokemon) {
        JSONArray abilities = pokemon.optJSONArray("abilities");
        if (abilities == null || abilities.isEmpty()) {
            return new JSONObject().put("name", "unknown").put("strength", 0);
        }
        return abilities.getJSONObject(random.nextInt(abilities.length()));
    }

    private String pokemonName(JSONObject pokemon) {
        for (String key : List.of("pokemon_name", "name", "pokemon")) {
            if (pokemon.has(key)) return pokemon.optString(key, "unknown");
        }
        return "unknown";
    }

    public int     currentRound()    { return roundNum.get(); }
    public boolean isOver()          { return score1.get() >= 3 || score2.get() >= 3; }
    public String  getWinner()       { return score1.get() >= 3 ? player1       : player2; }
    public String  getLoser()        { return score1.get() >= 3 ? player2       : player1; }
    public String  getWinnerUserId() { return score1.get() >= 3 ? player1UserId : player2UserId; }
    public String  getLoserUserId()  { return score1.get() >= 3 ? player2UserId : player1UserId; }
    public int     getScore1()       { return score1.get(); }
    public int     getScore2()       { return score2.get(); }
}
