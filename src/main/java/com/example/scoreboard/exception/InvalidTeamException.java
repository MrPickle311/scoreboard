package com.example.scoreboard.exception;

/** A team name or key is blank. */
public final class InvalidTeamException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final String input;

    public InvalidTeamException(String input) {
        super("Team name must not be blank: \"" + input + "\"");
        this.input = input;
    }

    /** The rejected value, exactly as given. */
    public String input() {
        return input;
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.INVALID_TEAM;
    }
}
