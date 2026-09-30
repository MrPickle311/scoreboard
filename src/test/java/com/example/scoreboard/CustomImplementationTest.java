package com.example.scoreboard;

import com.example.scoreboard.exception.CustomScoreboardException;
import com.example.scoreboard.exception.ErrorCode;
import com.example.scoreboard.exception.MatchNotLiveException;
import com.example.scoreboard.exception.RepositoryException;
import com.example.scoreboard.exception.ScoreboardException;
import com.example.scoreboard.exception.StandardErrorCode;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import com.example.scoreboard.storage.MatchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The library works with, and reports errors from, user-supplied implementations of its interfaces. */
class CustomImplementationTest {

    /** Delegates everything to the in-memory repository; tests override single methods. */
    private static class DelegatingRepository implements MatchRepository {
        final InMemoryMatchRepository delegate = new InMemoryMatchRepository();

        @Override public MatchId nextId() { return delegate.nextId(); }
        @Override public long nextStartSequence() { return delegate.nextStartSequence(); }
        @Override public Match newMatch(MatchId id, Team home, Team away, long seq) { return delegate.newMatch(id, home, away, seq); }
        @Override public void insert(Match match) { delegate.insert(match); }
        @Override public Optional<Match> findById(MatchId id) { return delegate.findById(id); }
        @Override public boolean replace(Match expected, Match updated) { return delegate.replace(expected, updated); }
        @Override public List<Match> findLive() { return delegate.findLive(); }
    }

    record NumericId(long value) implements MatchId {
        @Override public String toString() { return String.valueOf(value); }
    }

    @Test
    void numericIdsFromCustomRepository() {
        AtomicLong ids = new AtomicLong(41);
        Scoreboard scoreboard = new Scoreboard(new DelegatingRepository() {
            @Override public MatchId nextId() { return new NumericId(ids.incrementAndGet()); }
        });

        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.finishMatch(id);

        assertEquals(new NumericId(42), id);
        MatchNotLiveException e = assertThrows(MatchNotLiveException.class, () -> scoreboard.finishMatch(id));
        assertEquals("Cannot finish: match 42 is not live (status FINISHED)", e.getMessage());
    }

    /** A custom status: an abandoned match is not live, so it leaves the summary like a finished one. */
    record AbandonedStatus() implements MatchStatus {
        @Override public String name() { return "ABANDONED"; }
        @Override public boolean isLive() { return false; }
    }

    record AbandonableMatch(Match inner, MatchStatus status) implements Match {
        @Override public MatchId id() { return inner.id(); }
        @Override public Team homeTeam() { return inner.homeTeam(); }
        @Override public Team awayTeam() { return inner.awayTeam(); }
        @Override public int homeScore() { return inner.homeScore(); }
        @Override public int awayScore() { return inner.awayScore(); }
        @Override public long startSequence() { return inner.startSequence(); }
        @Override public long version() { return inner.version(); }
        @Override public Match withGoal(Side side, int delta) { return new AbandonableMatch(inner.withGoal(side, delta), status); }
        @Override public Match finished() { return new AbandonableMatch(inner.finished(), new AbandonedStatus()); }
    }

    @Test
    void customMatchTypeAndStatusRoundTrip() {
        Scoreboard scoreboard = new Scoreboard(new DelegatingRepository() {
            @Override public Match newMatch(MatchId id, Team home, Team away, long seq) {
                Match inner = Match.of(id, home, away, seq);
                return new AbandonableMatch(inner, inner.status());
            }
        });
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        assertInstanceOf(AbandonableMatch.class, scoreboard.scoreGoal(id, Side.HOME));
        Match ended = scoreboard.finishMatch(id);

        assertEquals("ABANDONED", ended.status().name());
        assertEquals(List.of(), scoreboard.getSummary());
        MatchNotLiveException e = assertThrows(MatchNotLiveException.class, () -> scoreboard.scoreGoal(id, Side.HOME));
        assertEquals("ABANDONED", e.status().name());
    }

    @Test
    void failureInRepositoryIsWrappedWithCause() {
        IllegalStateException cause = new IllegalStateException("database down");
        Scoreboard scoreboard = new Scoreboard(new DelegatingRepository() {
            @Override public boolean replace(Match expected, Match updated) { throw cause; }
        });
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        RepositoryException e = assertThrows(RepositoryException.class, () -> scoreboard.scoreGoal(id, Side.HOME));

        assertSame(cause, e.getCause());
        assertEquals("MatchRepository.replace", e.operation());
        assertEquals(Optional.of(id), e.matchId());
        assertEquals(StandardErrorCode.REPOSITORY_FAILURE, e.code());
    }

    @Test
    void matchThatDoesNotBumpVersionIsReported() {
        Scoreboard scoreboard = new Scoreboard(new DelegatingRepository() {
            @Override public Match newMatch(MatchId id, Team home, Team away, long seq) {
                return new StaleVersionMatch(Match.of(id, home, away, seq), 0);
            }
        });
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        RepositoryException e = assertThrows(RepositoryException.class, () -> scoreboard.scoreGoal(id, Side.HOME));

        assertEquals("Match.withGoal broke its contract for match " + id + ": must return version 1, got 0",
                e.getMessage());
    }

    /** Broken on purpose: withGoal keeps the old version. */
    record StaleVersionMatch(Match inner, long version) implements Match {
        @Override public MatchId id() { return inner.id(); }
        @Override public Team homeTeam() { return inner.homeTeam(); }
        @Override public Team awayTeam() { return inner.awayTeam(); }
        @Override public int homeScore() { return inner.homeScore(); }
        @Override public int awayScore() { return inner.awayScore(); }
        @Override public MatchStatus status() { return inner.status(); }
        @Override public long startSequence() { return inner.startSequence(); }
        @Override public Match withGoal(Side side, int delta) { return new StaleVersionMatch(inner.withGoal(side, delta), version); }
        @Override public Match finished() { return inner.finished(); }
    }

    enum StorageError implements ErrorCode { QUOTA_EXCEEDED }

    static final class QuotaExceededException extends CustomScoreboardException {
        private static final long serialVersionUID = 1L;

        QuotaExceededException() {
            super("Storage quota exceeded");
        }

        @Override
        public ErrorCode code() {
            return StorageError.QUOTA_EXCEEDED;
        }
    }

    @Test
    void customExceptionPassesThroughUnwrapped() {
        Scoreboard scoreboard = new Scoreboard(new DelegatingRepository() {
            @Override public void insert(Match match) { throw new QuotaExceededException(); }
        });

        ScoreboardException e = assertThrows(ScoreboardException.class, () -> scoreboard.startMatch("Mexico", "Canada"));

        String handled = switch (e) {
            case CustomScoreboardException custom -> custom.code().name();
            default -> "library error";
        };
        assertEquals("QUOTA_EXCEEDED", handled);
    }
}
