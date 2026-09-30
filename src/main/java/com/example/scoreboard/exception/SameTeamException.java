package com.example.scoreboard.exception;

import com.example.scoreboard.Team;

/** Home and away team of a new match are the same team (equal keys). */
public final class SameTeamException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final Team team;

    public SameTeamException(Team team) {
        super("Home and away team are the same: \"" + team.name() + "\" (key \"" + team.key() + "\")");
        this.team = team;
    }

    public Team team() {
        return team;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.SAME_TEAM;
    }
}
