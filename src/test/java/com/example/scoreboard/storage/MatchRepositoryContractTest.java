package com.example.scoreboard.storage;

import com.example.scoreboard.Match;
import com.example.scoreboard.MatchId;
import com.example.scoreboard.Side;
import com.example.scoreboard.Team;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the {@link MatchRepository} contract. Extend it and implement {@link #createRepository()} to verify
 * a custom implementation.
 */
public abstract class MatchRepositoryContractTest {

    protected MatchRepository repository;

    protected abstract MatchRepository createRepository();

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    private Match newMatch(String home, String away) {
        return repository.newMatch(repository.nextId(), Team.of(home), Team.of(away), repository.nextStartSequence());
    }

    private Match inserted(String home, String away) {
        Match match = newMatch(home, away);
        repository.insert(match);
        return match;
    }

    @Test
    void startSequenceIsStrictlyIncreasing() {
        long previous = repository.nextStartSequence();
        for (int i = 0; i < 100; i++) {
            long next = repository.nextStartSequence();
            assertTrue(next > previous, "sequence went from " + previous + " to " + next);
            previous = next;
        }
    }

    @Test
    void idsAreUnique() {
        Set<MatchId> ids = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            assertTrue(ids.add(repository.nextId()));
        }
    }

    @Test
    void insertedMatchCanBeFound() {
        Match match = inserted("Mexico", "Canada");

        assertEquals(match.id(), repository.findById(match.id()).orElseThrow().id());
        assertEquals(1, repository.findLive().size());
    }

    @Test
    void unknownIdIsEmpty() {
        assertTrue(repository.findById(repository.nextId()).isEmpty());
    }

    @Test
    void insertRejectsTeamAlreadyInLiveMatch() {
        Match live = inserted("Mexico", "Canada");

        TeamAlreadyPlayingException e = assertThrows(TeamAlreadyPlayingException.class,
                () -> repository.insert(newMatch("Spain", "mexico")));

        assertEquals("mexico", e.team().key());
        assertEquals(live.id(), e.liveMatchId());
        assertEquals(1, repository.findLive().size(), "rejected match must not be stored");
    }

    @Test
    void rejectedInsertDoesNotKeepFirstTeamClaimed() {
        inserted("Mexico", "Canada");

        // "Argentina" sorts before "Canada", so it is claimed first and must be released again
        assertThrows(TeamAlreadyPlayingException.class, () -> repository.insert(newMatch("Argentina", "Canada")));

        repository.insert(newMatch("Argentina", "Brazil"));
    }

    @Test
    void replaceSucceedsWhenVersionMatches() {
        Match match = inserted("Mexico", "Canada");
        Match updated = match.withGoal(Side.HOME, 1);

        assertTrue(repository.replace(match, updated));
        assertEquals(1, repository.findById(match.id()).orElseThrow().homeScore());
    }

    @Test
    void replaceFailsOnStaleVersion() {
        Match match = inserted("Mexico", "Canada");
        assertTrue(repository.replace(match, match.withGoal(Side.HOME, 1)));

        assertFalse(repository.replace(match, match.withGoal(Side.AWAY, 1)));
        Match stored = repository.findById(match.id()).orElseThrow();
        assertEquals(1, stored.homeScore());
        assertEquals(0, stored.awayScore());
    }

    @Test
    void finishingFreesTeamsAndLeavesLiveList() {
        Match match = inserted("Mexico", "Canada");

        assertTrue(repository.replace(match, match.finished()));

        assertTrue(repository.findLive().isEmpty());
        assertFalse(repository.findById(match.id()).orElseThrow().status().isLive(), "finished match is kept");
        assertEquals(List.of(match.id()), repository.findFinished().stream().map(Match::id).toList());
        repository.insert(newMatch("Mexico", "Canada"));
    }

    @Test
    void concurrentStartsWithSameTeamLetExactlyOneWin() throws Exception {
        AtomicInteger wins = new AtomicInteger();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                String opponent = "Opponent " + i;
                boolean mexicoAtHome = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        repository.insert(mexicoAtHome ? newMatch("Mexico", opponent) : newMatch(opponent, "Mexico"));
                        wins.incrementAndGet();
                    } catch (TeamAlreadyPlayingException expected) {
                        // lost the race
                    }
                });
            }
        }
        assertEquals(1, wins.get());
        assertEquals(1, repository.findLive().size());
    }

    @Test
    void concurrentOppositeStartsNeverBothFail() throws Exception {
        for (int round = 0; round < 200; round++) {
            repository = createRepository();
            AtomicInteger wins = new AtomicInteger();
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (String[] teams : List.of(new String[]{"X", "Y"}, new String[]{"Y", "X"})) {
                    executor.submit(() -> {
                        try {
                            repository.insert(newMatch(teams[0], teams[1]));
                            wins.incrementAndGet();
                        } catch (TeamAlreadyPlayingException expected) {
                            // lost the race
                        }
                    });
                }
            }
            assertEquals(1, wins.get(), "round " + round);
        }
    }

    @Test
    void findLiveReturnsSnapshot() {
        Match match = inserted("Mexico", "Canada");
        List<Match> before = repository.findLive();

        repository.replace(match, match.withGoal(Side.HOME, 1));

        assertSame(match, before.get(0), "an earlier snapshot must not change");
    }
}
