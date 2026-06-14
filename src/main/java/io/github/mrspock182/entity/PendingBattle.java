package io.github.mrspock182.entity;

import java.util.concurrent.atomic.AtomicReference;

public class PendingBattle {

    public final String challenger;
    public final String opponent;

    private final AtomicReference<String> challengerUserId = new AtomicReference<>();
    private final AtomicReference<String> opponentUserId = new AtomicReference<>();

    public PendingBattle(String challenger, String opponent) {
        this.challenger = challenger;
        this.opponent = opponent;
    }

    public boolean setUserId(String username, String userId) {
        if (username.equals(challenger)) challengerUserId.compareAndSet(null, userId);
        else if (username.equals(opponent)) opponentUserId.compareAndSet(null, userId);
        return challengerUserId.get() != null && opponentUserId.get() != null;
    }

    public String getChallengerUserId() { return challengerUserId.get(); }
    public String getOpponentUserId()   { return opponentUserId.get(); }
}
