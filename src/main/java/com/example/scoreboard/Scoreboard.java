package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidTeamException;
import com.example.scoreboard.exception.RepositoryException;
import com.example.scoreboard.exception.SameTeamException;
import com.example.scoreboard.exception.ScoreboardException;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import com.example.scoreboard.storage.MatchRepository;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Live football scoreboard: the library's entry point.
 *
 * <p>Thread-safe and non-blocking: writes use compare-and-set with retry on immutable {@link Match}
 * snapshots, so no update is lost and no lock is ever held, which suits virtual threads.
 */
public final class Scoreboard {

    private final MatchRepository repository;

    /** Scoreboard backed by an {@link InMemoryMatchRepository}. */
    public Scoreboard() {
        this(new InMemoryMatchRepository());
    }

    /** Scoreboard backed by a custom repository honouring the {@link MatchRepository} contract. */
    public Scoreboard(MatchRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * Starts a new match at 0–0 between two teams given by name (see {@link Team#of(String)}).
     *
     * @return the new match's id
     * @throws InvalidTeamException        if a name is blank
     * @throws SameTeamException           if both names denote the same team
     * @throws TeamAlreadyPlayingException if either team is already in a live match
     */
    public MatchId startMatch(String homeTeam, String awayTeam) {
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        return startMatch(Team.of(homeTeam), Team.of(awayTeam));
    }

    /**
     * Starts a new match at 0–0.
     *
     * @return the new match's id
     * @throws InvalidTeamException        if a team's key is blank
     * @throws SameTeamException           if both teams have the same key
     * @throws TeamAlreadyPlayingException if either team is already in a live match
     */
    public MatchId startMatch(Team homeTeam, Team awayTeam) {
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        String homeKey = keyOf(homeTeam);
        String awayKey = keyOf(awayTeam);
        if (homeKey.equals(awayKey)) {
            throw new SameTeamException(homeTeam);
        }

        MatchId id = call("MatchRepository.nextId", null, repository::nextId);
        if (id == null) {
            throw new RepositoryException("MatchRepository.nextId", null, "returned null");
        }
        long sequence = call("MatchRepository.nextStartSequence", id, repository::nextStartSequence);
        Match match = call("MatchRepository.newMatch", id, () -> repository.newMatch(id, homeTeam, awayTeam, sequence));
        checkNewMatch(match, id);
        run("MatchRepository.insert", id, () -> repository.insert(match));
        return id;
    }

    private static String keyOf(Team team) {
        String key = call("Team.key", null, team::key);
        if (key == null || key.isBlank()) {
            throw new InvalidTeamException(key);
        }
        return key;
    }

    private static void checkNewMatch(Match match, MatchId id) {
        String operation = "MatchRepository.newMatch";
        if (match == null) {
            throw new RepositoryException(operation, id, "returned null");
        }
        if (!id.equals(match.id())) {
            throw new RepositoryException(operation, id, "must keep id " + id + ", got " + match.id());
        }
        if (!match.status().isLive()) {
            throw new RepositoryException(operation, id, "must return a live match, got status " + match.status().name());
        }
        if (match.homeScore() != 0 || match.awayScore() != 0) {
            throw new RepositoryException(operation, id,
                    "must start at 0-0, got " + match.homeScore() + "-" + match.awayScore());
        }
    }

    /**
     * Runs user-supplied code, wrapping anything it throws (other than the library's own exceptions) in a
     * {@link RepositoryException} so callers only ever see {@link ScoreboardException}s.
     */
    static <T> T call(String operation, MatchId matchId, Supplier<T> action) {
        try {
            return action.get();
        } catch (ScoreboardException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RepositoryException(operation, matchId, e);
        }
    }

    static void run(String operation, MatchId matchId, Runnable action) {
        call(operation, matchId, () -> {
            action.run();
            return null;
        });
    }
}
