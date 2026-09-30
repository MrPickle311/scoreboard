package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidGoalsException;
import com.example.scoreboard.exception.InvalidTeamException;
import com.example.scoreboard.exception.MatchNotFoundException;
import com.example.scoreboard.exception.MatchNotLiveException;
import com.example.scoreboard.exception.RepositoryException;
import com.example.scoreboard.exception.SameTeamException;
import com.example.scoreboard.exception.ScoreOverflowException;
import com.example.scoreboard.exception.ScoreboardException;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import com.example.scoreboard.storage.MatchRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Live football scoreboard: the library's entry point.
 *
 * <p>Thread-safe and non-blocking: writes use compare-and-set with retry on immutable {@link Match}
 * snapshots, so no update is lost and no lock is ever held, which suits virtual threads.
 */
public final class Scoreboard {

    /** Total score descending; ties go to the most recently started match. */
    private static final Comparator<Match> SUMMARY_ORDER = Comparator
            .comparingLong(Match::totalScore).reversed()
            .thenComparing(Comparator.comparingLong(Match::startSequence).reversed());

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

    /**
     * Adds one goal to the given side. Concurrent goals are never lost.
     *
     * @return the match as it stands after this goal
     * @throws MatchNotFoundException  if no match has this id
     * @throws MatchNotLiveException   if the match is not live
     * @throws ScoreOverflowException  if the score is already {@link Integer#MAX_VALUE}
     */
    public Match scoreGoal(MatchId id, Side side) {
        return changeScore(id, side, +1);
    }

    /**
     * Removes one goal from the given side, e.g. after a VAR decision. A score never goes below 0.
     *
     * @return the match as it stands after the cancellation
     * @throws MatchNotFoundException if no match has this id
     * @throws MatchNotLiveException  if the match is not live
     * @throws InvalidGoalsException  if that side's score is already 0
     */
    public Match cancelGoal(MatchId id, Side side) {
        return changeScore(id, side, -1);
    }

    /**
     * Finishes a match. It leaves the summary but is kept as history, and both teams can start new matches.
     *
     * @return the finished match
     * @throws MatchNotFoundException if no match has this id
     * @throws MatchNotLiveException  if the match is already not live
     */
    public Match finishMatch(MatchId id) {
        Objects.requireNonNull(id, "id must not be null");
        while (true) {
            Match current = load(id);
            if (!current.status().isLive()) {
                throw new MatchNotLiveException(id, current.status(), "finish");
            }
            Match finished = call("Match.finished", id, current::finished);
            checkFinished(current, finished);
            if (replace(current, finished)) {
                return finished;
            }
        }
    }

    private static void checkFinished(Match current, Match finished) {
        String operation = "Match.finished";
        checkSuccessor(operation, current, finished);
        if (finished.status().isLive()) {
            throw new RepositoryException(operation, current.id(),
                    "must return a non-live status, got " + finished.status().name());
        }
        if (finished.homeScore() != current.homeScore() || finished.awayScore() != current.awayScore()) {
            throw new RepositoryException(operation, current.id(), "must keep the score "
                    + current.homeScore() + "-" + current.awayScore() + ", got "
                    + finished.homeScore() + "-" + finished.awayScore());
        }
    }

    /**
     * Live matches ordered by total score (descending); matches with the same total are ordered with the most
     * recently started first.
     *
     * <p>A point-in-time snapshot: each match is internally consistent, but a write in flight at that instant
     * may not be included. The returned list is unmodifiable.
     */
    public List<Match> getSummary() {
        List<Match> live = call("MatchRepository.findLive", null, repository::findLive);
        return live.stream()
                .filter(match -> match.status().isLive())
                .sorted(SUMMARY_ORDER)
                .toList();
    }

    /** Lock-free compare-and-set loop: read, validate, build new snapshot, CAS; retry if another write won. */
    private Match changeScore(MatchId id, Side side, int delta) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(side, "side must not be null");
        String action = delta > 0 ? "score a goal" : "cancel a goal";
        while (true) {
            Match current = load(id);
            if (!current.status().isLive()) {
                throw new MatchNotLiveException(id, current.status(), action);
            }
            int score = current.score(side);
            if (delta < 0 && score == 0) {
                throw new InvalidGoalsException(id, side);
            }
            if (delta > 0 && score == Integer.MAX_VALUE) {
                throw new ScoreOverflowException(id, side, score, delta);
            }
            Match updated = call("Match.withGoal", id, () -> current.withGoal(side, delta));
            checkGoal(current, updated, side, delta);
            if (replace(current, updated)) {
                return updated;
            }
        }
    }

    private Match load(MatchId id) {
        return call("MatchRepository.findById", id, () -> repository.findById(id))
                .orElseThrow(() -> new MatchNotFoundException(id));
    }

    private boolean replace(Match expected, Match updated) {
        return call("MatchRepository.replace", expected.id(), () -> repository.replace(expected, updated));
    }

    private static void checkGoal(Match current, Match updated, Side side, int delta) {
        String operation = "Match.withGoal";
        checkSuccessor(operation, current, updated);
        Side other = side == Side.HOME ? Side.AWAY : Side.HOME;
        if (updated.score(side) != current.score(side) + delta || updated.score(other) != current.score(other)) {
            throw new RepositoryException(operation, current.id(),
                    "must change only the " + side.label() + " score by " + delta + ": "
                            + current.homeScore() + "-" + current.awayScore() + " became "
                            + updated.homeScore() + "-" + updated.awayScore());
        }
    }

    /** Checks the rules every change of a match must follow: new instance, same id, version + 1. */
    private static void checkSuccessor(String operation, Match current, Match updated) {
        if (updated == null) {
            throw new RepositoryException(operation, current.id(), "returned null");
        }
        if (updated == current) {
            throw new RepositoryException(operation, current.id(), "must return a new instance");
        }
        if (!current.id().equals(updated.id())) {
            throw new RepositoryException(operation, current.id(), "must keep id " + current.id() + ", got " + updated.id());
        }
        if (updated.version() != current.version() + 1) {
            throw new RepositoryException(operation, current.id(),
                    "must return version " + (current.version() + 1) + ", got " + updated.version());
        }
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
