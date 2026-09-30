package com.example.scoreboard;

import com.example.scoreboard.exception.MatchNotLiveException;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs operations concurrently on virtual threads and asserts the final state, so the tests are
 * deterministic even though scheduling is not.
 */
class ConcurrencyTest {

    private final Scoreboard scoreboard = new Scoreboard();

    /** Runs the tasks on virtual threads and rethrows the first failure, so worker assertions count. */
    private static void concurrently(int tasks, IntTask task) {
        List<Future<?>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = IntStream.range(0, tasks)
                    .<Future<?>>mapToObj(i -> executor.submit(() -> task.run(i)))
                    .toList();
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new AssertionError("concurrent task failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    @FunctionalInterface
    private interface IntTask {
        void run(int i);
    }

    @Test
    void noGoalIsLostUnderContention() {
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        concurrently(10_000, i -> scoreboard.scoreGoal(id, i % 2 == 0 ? Side.HOME : Side.AWAY));

        Match match = scoreboard.getSummary().get(0);
        assertEquals(5_000, match.homeScore());
        assertEquals(5_000, match.awayScore());
    }

    @Test
    void concurrentScoresAndCancelsBalanceOut() {
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        concurrently(1_000, i -> scoreboard.scoreGoal(id, Side.HOME));

        concurrently(2_000, i -> {
            if (i % 2 == 0) {
                scoreboard.scoreGoal(id, Side.HOME);
            } else {
                scoreboard.cancelGoal(id, Side.HOME);
            }
        });

        assertEquals(1_000, scoreboard.getSummary().get(0).homeScore());
    }

    @Test
    void exactlyOneOfManyStartsWithSameTeamWins() {
        AtomicInteger started = new AtomicInteger();

        concurrently(500, i -> {
            try {
                scoreboard.startMatch("Mexico", "Opponent " + i);
                started.incrementAndGet();
            } catch (TeamAlreadyPlayingException expected) {
                // another start won
            }
        });

        assertEquals(1, started.get());
        assertEquals(1, scoreboard.getSummary().size());
    }

    @RepeatedTest(20)
    void goalsRacingAFinishAreEitherCountedOrRejected() {
        MatchId id = scoreboard.startMatch("Mexico", "Canada");
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<Match> finished = new AtomicReference<>();

        concurrently(2_000, i -> {
            if (i == 1_000) {
                finished.set(scoreboard.finishMatch(id));
                return;
            }
            try {
                scoreboard.scoreGoal(id, Side.HOME);
                accepted.incrementAndGet();
            } catch (MatchNotLiveException e) {
                rejected.incrementAndGet();
            }
        });

        assertEquals(accepted.get(), finished.get().homeScore(), "every accepted goal is in the final score");
        assertEquals(1_999, accepted.get() + rejected.get());
        assertTrue(scoreboard.getSummary().isEmpty());
    }

    @Test
    void summaryCanBeReadWhileMatchesChange() {
        List<MatchId> ids = IntStream.range(0, 50)
                .mapToObj(i -> scoreboard.startMatch("Home " + i, "Away " + i))
                .toList();

        concurrently(5_000, i -> {
            if (i % 5 == 0) {
                List<Match> summary = scoreboard.getSummary();
                for (int j = 1; j < summary.size(); j++) {
                    assertTrue(summary.get(j - 1).totalScore() >= summary.get(j).totalScore());
                }
            } else {
                scoreboard.scoreGoal(ids.get(i % ids.size()), Side.HOME);
            }
        });

        assertEquals(4_000, scoreboard.getSummary().stream().mapToLong(Match::totalScore).sum());
    }
}
