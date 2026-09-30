package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidGoalsException;
import com.example.scoreboard.exception.MatchNotFoundException;
import com.example.scoreboard.exception.ScoreOverflowException;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalTest {

    private final InMemoryMatchRepository repository = new InMemoryMatchRepository();
    private final Scoreboard scoreboard = new Scoreboard(repository);
    private final MatchId id = scoreboard.startMatch("Mexico", "Canada");

    @Test
    void scoreGoalAddsOneToThatSide() {
        scoreboard.scoreGoal(id, Side.AWAY);
        Match match = scoreboard.scoreGoal(id, Side.AWAY);

        assertEquals(0, match.homeScore());
        assertEquals(2, match.awayScore());
        assertEquals(2, repository.findById(id).orElseThrow().awayScore());
    }

    @Test
    void cancelGoalRemovesOne() {
        scoreboard.scoreGoal(id, Side.HOME);
        scoreboard.scoreGoal(id, Side.HOME);

        Match match = scoreboard.cancelGoal(id, Side.HOME);

        assertEquals(1, match.homeScore());
    }

    @Test
    void cancelGoalNeverGoesBelowZero() {
        InvalidGoalsException e = assertThrows(InvalidGoalsException.class, () -> scoreboard.cancelGoal(id, Side.HOME));

        assertEquals(id, e.matchId());
        assertEquals(Side.HOME, e.side());
        assertEquals("Cannot cancel a goal: home score of match " + id + " is already 0", e.getMessage());
        assertEquals(0, repository.findById(id).orElseThrow().homeScore());
    }

    @Test
    void unknownMatchIsReported() {
        MatchId unknown = MatchId.random();

        MatchNotFoundException e = assertThrows(MatchNotFoundException.class, () -> scoreboard.scoreGoal(unknown, Side.HOME));

        assertEquals(unknown, e.matchId());
        assertEquals("No match with id " + unknown, e.getMessage());
    }

    @Test
    void scoreCannotOverflow() {
        Match current = repository.findById(id).orElseThrow();
        Match atMax = new DefaultMatch(id, current.homeTeam(), current.awayTeam(), Integer.MAX_VALUE, 0,
                current.status(), current.startSequence(), current.version() + 1);
        repository.replace(current, atMax);

        ScoreOverflowException e = assertThrows(ScoreOverflowException.class, () -> scoreboard.scoreGoal(id, Side.HOME));

        assertEquals(Integer.MAX_VALUE, e.current());
        assertEquals("Home score of match " + id + " would overflow: 2147483647 + 1", e.getMessage());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> scoreboard.scoreGoal(null, Side.HOME));
        assertThrows(NullPointerException.class, () -> scoreboard.scoreGoal(id, null));
    }
}
