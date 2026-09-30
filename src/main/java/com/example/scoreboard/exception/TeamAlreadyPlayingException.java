package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;
import com.example.scoreboard.Team;

/**
 * A team cannot start a match because it is already playing in a live one. Custom repositories throw this
 * from {@code insert} to signal a taken team.
 */
public final class TeamAlreadyPlayingException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final Team team;
    private final MatchId liveMatchId;

    public TeamAlreadyPlayingException(Team team, MatchId liveMatchId) {
        super("Team \"" + team.name() + "\" is already playing in live match " + liveMatchId);
        this.team = team;
        this.liveMatchId = liveMatchId;
    }

    public Team team() {
        return team;
    }

    /** The live match the team is playing in. */
    public MatchId liveMatchId() {
        return liveMatchId;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.TEAM_ALREADY_PLAYING;
    }
}
