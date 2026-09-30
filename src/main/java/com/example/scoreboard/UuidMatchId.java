package com.example.scoreboard;

import java.util.Objects;
import java.util.UUID;

/** Default {@link MatchId}. */
record UuidMatchId(UUID value) implements MatchId {

    UuidMatchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
