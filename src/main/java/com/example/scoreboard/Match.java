package com.example.scoreboard;

/**
 * An immutable snapshot of a match.
 *
 * <p>Users may provide their own implementation via
 * {@link com.example.scoreboard.storage.MatchRepository#newMatch}. The library never mutates a match: every
 * change goes through {@link #withGoal} or {@link #finished()}, which must return a <b>new</b> instance with
 * {@code version() + 1}. The library checks this and reports a breach as a
 * {@link com.example.scoreboard.exception.RepositoryException}.
 */
public interface Match {

    MatchId id();

    Team homeTeam();

    Team awayTeam();

    int homeScore();

    int awayScore();

    MatchStatus status();

    /** Start order issued by the repository; higher means started later. */
    long startSequence();

    /** Incremented by every change; used for compare-and-set. */
    long version();

    /** Sum of both scores, as {@code long} so it cannot overflow. */
    default long totalScore() {
        return (long) homeScore() + awayScore();
    }

    /** Score of the given side. */
    default int score(Side side) {
        return side == Side.HOME ? homeScore() : awayScore();
    }

    /**
     * Returns a copy with {@code delta} (+1 or -1) added to the given side's score and {@code version + 1}.
     * The caller has already checked the result stays within {@code 0..Integer.MAX_VALUE}.
     */
    Match withGoal(Side side, int delta);

    /** Returns a copy with a non-live status and {@code version + 1}. */
    Match finished();

    /** Default implementation: a new live match at 0–0, version 0. */
    static Match of(MatchId id, Team homeTeam, Team awayTeam, long startSequence) {
        return new DefaultMatch(id, homeTeam, awayTeam, 0, 0, StandardMatchStatus.IN_PROGRESS, startSequence, 0);
    }
}
