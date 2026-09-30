package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidTeamException;
import com.example.scoreboard.exception.SameTeamException;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;
import com.example.scoreboard.storage.InMemoryMatchRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartMatchTest {

    private final InMemoryMatchRepository repository = new InMemoryMatchRepository();
    private final Scoreboard scoreboard = new Scoreboard(repository);

    @Test
    void startsMatchAtNilNil() {
        MatchId id = scoreboard.startMatch("Mexico", "Canada");

        Match match = repository.findById(id).orElseThrow();
        assertEquals("Mexico", match.homeTeam().name());
        assertEquals("Canada", match.awayTeam().name());
        assertEquals(0, match.homeScore());
        assertEquals(0, match.awayScore());
    }

    @Test
    void eachMatchGetsItsOwnId() {
        assertNotEquals(scoreboard.startMatch("Mexico", "Canada"), scoreboard.startMatch("Spain", "Brazil"));
    }

    @Test
    void rejectsSameTeamOnBothSidesIgnoringCaseAndSpaces() {
        SameTeamException e = assertThrows(SameTeamException.class, () -> scoreboard.startMatch("Mexico", " mexico "));

        assertEquals("mexico", e.team().key());
        assertEquals("Home and away team are the same: \"Mexico\" (key \"mexico\")", e.getMessage());
    }

    @Test
    void rejectsBlankTeam() {
        assertThrows(InvalidTeamException.class, () -> scoreboard.startMatch("Mexico", " "));
    }

    @Test
    void rejectsNullTeam() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> scoreboard.startMatch("Mexico", (String) null));

        assertEquals("awayTeam must not be null", e.getMessage());
    }

    @Test
    void rejectsTeamAlreadyPlaying() {
        MatchId live = scoreboard.startMatch("Mexico", "Canada");

        TeamAlreadyPlayingException e = assertThrows(TeamAlreadyPlayingException.class,
                () -> scoreboard.startMatch("Canada", "Spain"));

        assertEquals(live, e.liveMatchId());
        assertEquals("Team \"Canada\" is already playing in live match " + live, e.getMessage());
    }

    @Test
    void acceptsCustomTeamImplementation() {
        record FifaTeam(String name, String key) implements Team { }

        assertDoesNotThrow(() -> scoreboard.startMatch(new FifaTeam("Mexico", "MEX"), new FifaTeam("Canada", "CAN")));
    }
}
