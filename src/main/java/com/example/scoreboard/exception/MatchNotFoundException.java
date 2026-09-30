package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;

/** No match exists with the given id. */
public final class MatchNotFoundException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final MatchId matchId;

    public MatchNotFoundException(MatchId matchId) {
        super("No match with id " + matchId);
        this.matchId = matchId;
    }

    public MatchId matchId() {
        return matchId;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.MATCH_NOT_FOUND;
    }
}
