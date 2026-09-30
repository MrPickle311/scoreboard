package com.example.scoreboard.exception;

/**
 * Extension point for user-defined errors, e.g. from a custom repository. Pair it with your own
 * {@link ErrorCode}.
 */
public non-sealed abstract class CustomScoreboardException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    protected CustomScoreboardException(String message) {
        super(message);
    }

    protected CustomScoreboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
