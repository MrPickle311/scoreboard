package com.example.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishedMatchesTest {

    private final Scoreboard scoreboard = new Scoreboard();

    private List<String> finished() {
        return scoreboard.getFinishedMatches().stream().map(Match::toString).toList();
    }

    @Test
    void emptyWhenNothingFinished() {
        scoreboard.startMatch("Mexico", "Canada");

        assertTrue(scoreboard.getFinishedMatches().isEmpty());
    }

    @Test
    void listsFinishedMatchesWithFinalScoreMostRecentlyStartedFirst() {
        MatchId first = scoreboard.startMatch("Mexico", "Canada");
        MatchId second = scoreboard.startMatch("Spain", "Brazil");
        scoreboard.startMatch("Germany", "France");
        scoreboard.scoreGoal(first, Side.AWAY);
        scoreboard.finishMatch(first);
        scoreboard.finishMatch(second);

        assertEquals(List.of("Spain 0 - Brazil 0", "Mexico 0 - Canada 1"), finished());
    }

    @Test
    void sameTeamsCanAppearSeveralTimes() {
        scoreboard.finishMatch(scoreboard.startMatch("Mexico", "Canada"));
        MatchId rematch = scoreboard.startMatch("Canada", "Mexico");
        scoreboard.scoreGoal(rematch, Side.HOME);
        scoreboard.finishMatch(rematch);

        assertEquals(List.of("Canada 1 - Mexico 0", "Mexico 0 - Canada 0"), finished());
    }

    @Test
    void isUnmodifiable() {
        scoreboard.finishMatch(scoreboard.startMatch("Mexico", "Canada"));

        assertThrows(UnsupportedOperationException.class, () -> scoreboard.getFinishedMatches().clear());
    }
}
