package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTest {

    private final Match match = Match.of(MatchId.random(), Team.of("Mexico"), Team.of("Canada"), 7);

    @Test
    void newMatchIsLiveAtNilNil() {
        assertAll(
                () -> assertEquals(0, match.homeScore()),
                () -> assertEquals(0, match.awayScore()),
                () -> assertTrue(match.status().isLive()),
                () -> assertEquals(7, match.startSequence()),
                () -> assertEquals(0, match.version()));
    }

    @Test
    void withGoalReturnsNewSnapshotWithBumpedVersion() {
        Match updated = match.withGoal(Side.AWAY, 1);

        assertEquals(1, updated.awayScore());
        assertEquals(0, updated.homeScore());
        assertEquals(1, updated.version());
        assertEquals(0, match.awayScore(), "original snapshot must not change");
    }

    @Test
    void finishedIsNotLiveAndKeepsScore() {
        Match finished = match.withGoal(Side.HOME, 1).finished();

        assertFalse(finished.status().isLive());
        assertEquals(StandardMatchStatus.FINISHED, finished.status());
        assertEquals(1, finished.homeScore());
        assertEquals(2, finished.version());
    }

    @Test
    void totalScoreDoesNotOverflow() {
        Match huge = new DefaultMatch(match.id(), match.homeTeam(), match.awayTeam(),
                Integer.MAX_VALUE, Integer.MAX_VALUE, StandardMatchStatus.IN_PROGRESS, 1, 0);

        assertEquals(2L * Integer.MAX_VALUE, huge.totalScore());
    }

    @Test
    void readableToString() {
        assertEquals("Mexico 0 - Canada 1", match.withGoal(Side.AWAY, 1).toString());
    }
}
