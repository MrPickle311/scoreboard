package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidTeamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamTest {

    @Test
    void trimsNameAndLowerCasesKey() {
        Team team = Team.of("  Mexico ");

        assertEquals("Mexico", team.name());
        assertEquals("mexico", team.key());
    }

    @Test
    void differentSpellingsOfSameTeamShareKey() {
        assertEquals(Team.of("Mexico").key(), Team.of(" MEXICO").key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlankName(String blank) {
        InvalidTeamException e = assertThrows(InvalidTeamException.class, () -> Team.of(blank));

        assertEquals(blank, e.input());
        assertEquals("Team name must not be blank: \"" + blank + "\"", e.getMessage());
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () -> Team.of(null));
    }
}
