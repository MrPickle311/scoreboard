package com.example.scoreboard.storage;

import com.example.scoreboard.Match;
import com.example.scoreboard.MatchId;
import com.example.scoreboard.Team;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;

import java.util.List;
import java.util.Optional;

/**
 * Storage for matches. Its methods are the atomic primitives the scoreboard's lock-free design builds on.
 *
 * <h2>Contract every implementation must honour</h2>
 * <ul>
 *   <li>{@link #insert} fails with {@link TeamAlreadyPlayingException} if either team (by {@link Team#key()})
 *       is already in a live match, atomically with the write.</li>
 *   <li>{@link #replace} succeeds only if the stored version equals {@code expected.version()}. When
 *       {@code updated} is no longer live, both teams become free again.</li>
 *   <li>{@link #nextStartSequence} never returns the same value twice and never goes backwards.</li>
 *   <li>{@link #nextId} never returns an id equal to one it issued before.</li>
 *   <li>No method blocks waiting on another caller (the scoreboard may run on virtual threads).</li>
 * </ul>
 *
 * <p>A database could meet it with a unique partial index on team where the match is live,
 * {@code UPDATE ... WHERE id = ? AND version = ?} for {@code replace}, and a sequence for
 * {@code nextStartSequence}. Extend {@code MatchRepositoryContractTest} (test sources) to verify an
 * implementation.
 */
public interface MatchRepository {

    /** A new, never-issued match id. */
    MatchId nextId();

    /** A strictly increasing start sequence; decides "most recently started". */
    long nextStartSequence();

    /** Creates a new live match at 0–0. Override to store a custom {@link Match} type. */
    default Match newMatch(MatchId id, Team homeTeam, Team awayTeam, long startSequence) {
        return Match.of(id, homeTeam, awayTeam, startSequence);
    }

    /**
     * Stores a new match.
     *
     * @throws TeamAlreadyPlayingException if either team is already in a live match; nothing is stored
     */
    void insert(Match match);

    Optional<Match> findById(MatchId id);

    /**
     * Compare-and-set: stores {@code updated} only if the stored match still has {@code expected.version()}.
     *
     * @return {@code true} if stored, {@code false} if another write got there first
     */
    boolean replace(Match expected, Match updated);

    /** Point-in-time snapshot of all live matches, in any order. */
    List<Match> findLive();

    /** Point-in-time snapshot of all matches that are no longer live (the kept history), in any order. */
    List<Match> findFinished();
}
