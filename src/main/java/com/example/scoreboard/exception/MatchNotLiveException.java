package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;
import com.example.scoreboard.MatchStatus;

/** The operation needs a live match, but the match is not live (e.g. finished). */
public final class MatchNotLiveException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final MatchId matchId;
    private final MatchStatus status;

    /**
     * @param action what was attempted, e.g. {@code "score a goal"}; used in the message
     */
    public MatchNotLiveException(MatchId matchId, MatchStatus status, String action) {
        super("Cannot " + action + ": match " + matchId + " is not live (status " + status.name() + ")");
        this.matchId = matchId;
        this.status = status;
    }

    public MatchId matchId() {
        return matchId;
    }

    public MatchStatus status() {
        return status;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.MATCH_NOT_LIVE;
    }
}
