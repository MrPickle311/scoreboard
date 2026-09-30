package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryTest {

    private final Scoreboard scoreboard = new Scoreboard();

    private MatchId play(String home, int homeGoals, String away, int awayGoals) {
        MatchId id = scoreboard.startMatch(home, away);
        for (int i = 0; i < homeGoals; i++) {
            scoreboard.scoreGoal(id, Side.HOME);
        }
        for (int i = 0; i < awayGoals; i++) {
            scoreboard.scoreGoal(id, Side.AWAY);
        }
        return id;
    }

    private List<String> summary() {
        return scoreboard.getSummary().stream().map(Match::toString).toList();
    }

    @Test
    void specificationExample() {
        play("Mexico", 0, "Canada", 5);
        play("Spain", 10, "Brazil", 2);
        play("Germany", 2, "France", 2);
        play("Uruguay", 6, "Italy", 6);
        play("Argentina", 3, "Australia", 1);

        assertEquals(List.of(
                "Uruguay 6 - Italy 6",
                "Spain 10 - Brazil 2",
                "Mexico 0 - Canada 5",
                "Argentina 3 - Australia 1",
                "Germany 2 - France 2"), summary());
    }

    @Test
    void emptyWhenNoMatches() {
        assertTrue(scoreboard.getSummary().isEmpty());
    }

    @Test
    void finishedMatchesAreExcluded() {
        MatchId finished = play("Mexico", 3, "Canada", 0);
        play("Spain", 1, "Brazil", 0);

        scoreboard.finishMatch(finished);

        assertEquals(List.of("Spain 1 - Brazil 0"), summary());
    }

    @Test
    void tieOrderFollowsStartOrderNotLastGoal() {
        MatchId first = play("Mexico", 0, "Canada", 0);
        play("Spain", 1, "Brazil", 0);
        scoreboard.scoreGoal(first, Side.HOME);

        assertEquals(List.of("Spain 1 - Brazil 0", "Mexico 1 - Canada 0"), summary());
    }

    @Test
    void cancelledGoalMovesMatchDown() {
        MatchId id = play("Mexico", 2, "Canada", 0);
        play("Spain", 1, "Brazil", 0);

        scoreboard.cancelGoal(id, Side.HOME);
        scoreboard.cancelGoal(id, Side.HOME);

        assertEquals(List.of("Spain 1 - Brazil 0", "Mexico 0 - Canada 0"), summary());
    }

    @Test
    void summaryIsUnmodifiable() {
        play("Mexico", 0, "Canada", 0);

        assertThrows(UnsupportedOperationException.class, () -> scoreboard.getSummary().clear());
    }
}
