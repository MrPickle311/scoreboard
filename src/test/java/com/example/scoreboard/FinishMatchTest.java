package com.example.scoreboard;

import com.example.scoreboard.exception.MatchNotFoundException;
import com.example.scoreboard.exception.MatchNotLiveException;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinishMatchTest {

    private final InMemoryMatchRepository repository = new InMemoryMatchRepository();
    private final Scoreboard scoreboard = new Scoreboard(repository);
    private final MatchId id = scoreboard.startMatch("Mexico", "Canada");

    @Test
    void finishedMatchIsKeptWithFinalScore() {
        scoreboard.scoreGoal(id, Side.AWAY);

        Match finished = scoreboard.finishMatch(id);

        assertFalse(finished.status().isLive());
        Match stored = repository.findById(id).orElseThrow();
        assertEquals(StandardMatchStatus.FINISHED, stored.status());
        assertEquals(1, stored.awayScore());
    }

    @Test
    void teamsCanPlayAgainAfterFinish() {
        scoreboard.finishMatch(id);

        assertDoesNotThrow(() -> scoreboard.startMatch("Canada", "Mexico"));
    }

    @Test
    void cannotFinishTwice() {
        scoreboard.finishMatch(id);

        MatchNotLiveException e = assertThrows(MatchNotLiveException.class, () -> scoreboard.finishMatch(id));

        assertEquals(StandardMatchStatus.FINISHED, e.status());
        assertEquals("Cannot finish: match " + id + " is not live (status FINISHED)", e.getMessage());
    }

    @Test
    void cannotScoreOrCancelInFinishedMatch() {
        scoreboard.scoreGoal(id, Side.HOME);
        scoreboard.finishMatch(id);

        MatchNotLiveException e = assertThrows(MatchNotLiveException.class, () -> scoreboard.scoreGoal(id, Side.HOME));
        assertEquals("Cannot score a goal: match " + id + " is not live (status FINISHED)", e.getMessage());
        assertThrows(MatchNotLiveException.class, () -> scoreboard.cancelGoal(id, Side.HOME));
    }

    @Test
    void unknownMatchIsReported() {
        assertThrows(MatchNotFoundException.class, () -> scoreboard.finishMatch(MatchId.random()));
    }
}
