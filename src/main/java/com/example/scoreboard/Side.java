package com.example.scoreboard;

/**
 * Which team of a match a goal belongs to. The home team is the one listed first.
 */
public enum Side {
    HOME,
    AWAY;

    /** Lower-case label used in messages, e.g. {@code "home"}. */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
