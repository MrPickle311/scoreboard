package com.example.scoreboard.exception;

import com.example.scoreboard.MatchId;

import java.util.Optional;

/**
 * User-supplied code (a repository, {@code Team}, {@code Match} or {@code MatchId} implementation) failed or
 * broke its contract. When it threw, the original exception is the {@link #getCause() cause}.
 */
public final class RepositoryException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    private final String operation;
    private final MatchId matchId;

    /** User code threw {@code cause} while running {@code operation}. */
    public RepositoryException(String operation, MatchId matchId, Throwable cause) {
        super(operation + " failed" + forMatch(matchId) + ": " + cause, cause);
        this.operation = operation;
        this.matchId = matchId;
    }

    /** User code returned a result that breaks its contract; {@code brokenRule} says how. */
    public RepositoryException(String operation, MatchId matchId, String brokenRule) {
        super(operation + " broke its contract" + forMatch(matchId) + ": " + brokenRule);
        this.operation = operation;
        this.matchId = matchId;
    }

    private static String forMatch(MatchId matchId) {
        return matchId == null ? "" : " for match " + matchId;
    }

    /** The failing call, e.g. {@code "MatchRepository.replace"}. */
    public String operation() {
        return operation;
    }

    /** The match involved, if one was known at the time. */
    public Optional<MatchId> matchId() {
        return Optional.ofNullable(matchId);
    }

    @Override
    public ErrorCode code() {
        return StandardErrorCode.REPOSITORY_FAILURE;
    }
}
