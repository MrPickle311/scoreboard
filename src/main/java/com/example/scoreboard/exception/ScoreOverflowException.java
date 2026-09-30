package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;
import com.example.scoreboard.Side;

/** A goal would push a score past {@link Integer#MAX_VALUE}. */
public final class ScoreOverflowException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final MatchId matchId;
    private final Side side;
    private final int current;
    private final int increment;

    public ScoreOverflowException(MatchId matchId, Side side, int current, int increment) {
        super(capitalize(side.label()) + " score of match " + matchId + " would overflow: " + current + " + " + increment);
        this.matchId = matchId;
        this.side = side;
        this.current = current;
        this.increment = increment;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public MatchId matchId() {
        return matchId;
    }

    public Side side() {
        return side;
    }

    public int current() {
        return current;
    }

    public int increment() {
        return increment;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.SCORE_OVERFLOW;
    }
}
