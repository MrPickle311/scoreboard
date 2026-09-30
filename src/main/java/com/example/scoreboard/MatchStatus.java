package com.example.scoreboard;

/**
 * Status of a match. The library reads a status only through {@link #isLive()}, never by identity.
 *
 * <p>A live match holds its teams, accepts goals and appears in the summary; a non-live match is history.
 * Users may add their own statuses, e.g. {@code HALF_TIME} (live) or {@code ABANDONED} (not live).
 */
public interface MatchStatus {

    /** Name used in messages, e.g. {@code "FINISHED"}. */
    String name();

    /** Whether the match is still being played. */
    boolean isLive();
}
