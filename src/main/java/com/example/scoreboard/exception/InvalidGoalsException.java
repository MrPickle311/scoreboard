package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;
import com.example.scoreboard.Side;

/** A goal cannot be cancelled because that side's score is already 0. */
public final class InvalidGoalsException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final MatchId matchId;
    private final Side side;

    public InvalidGoalsException(MatchId matchId, Side side) {
        super("Cannot cancel a goal: " + side.label() + " score of match " + matchId + " is already 0");
        this.matchId = matchId;
        this.side = side;
    }

    public MatchId matchId() {
        return matchId;
    }

    public Side side() {
        return side;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.INVALID_GOALS;
    }
}
