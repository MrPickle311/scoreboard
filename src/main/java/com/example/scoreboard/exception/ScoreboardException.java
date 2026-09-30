package com.example.scoreboard.exception;

/**
 * Base of every error the scoreboard reports.
 *
 * <p>Sealed, so callers can {@code switch} over it exhaustively. Users add their own errors by extending
 * {@link CustomScoreboardException}, the one open branch.
 */
public sealed abstract class ScoreboardException extends RuntimeException
        permits InvalidTeamException, SameTeamException, InvalidGoalsException,
                TeamAlreadyPlayingException, MatchNotFoundException, MatchNotLiveException,
                ScoreOverflowException, RepositoryException, CustomScoreboardException {

    private static final long serialVersionUID = 1L;

    protected ScoreboardException(String message) {
        super(message);
    }

    protected ScoreboardException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Stable code identifying the kind of error. */
    public abstract ErrorCode code();
}
