package com.example.scoreboard;

import com.example.scoreboard.exception.InvalidTeamException;

import java.util.Locale;
import java.util.Objects;

/** Default {@link Team}: trimmed display name, lower-cased key. */
record SimpleTeam(String name, String key) implements Team {

    static SimpleTeam of(String name) {
        Objects.requireNonNull(name, "team name must not be null");
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidTeamException(name);
        }
        return new SimpleTeam(trimmed, trimmed.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return name;
    }
}
