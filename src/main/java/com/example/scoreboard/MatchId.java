package com.example.scoreboard;

import java.util.UUID;

/**
 * Identity of a match, issued by the storage via
 * {@link com.example.scoreboard.storage.MatchRepository#nextId()}.
 *
 * <p>Users may provide their own implementation (e.g. a numeric database id). The library treats ids as
 * opaque and compares them only with {@code equals}/{@code hashCode}, so implementations must be value-based
 * (a record is the easy choice) and should have a readable {@code toString()}, which appears in error messages.
 */
public interface MatchId {

    /** Default id backed by a random UUID. */
    static MatchId random() {
        return new UuidMatchId(UUID.randomUUID());
    }

    /** Default id backed by the given UUID. */
    static MatchId of(UUID value) {
        return new UuidMatchId(value);
    }
}
