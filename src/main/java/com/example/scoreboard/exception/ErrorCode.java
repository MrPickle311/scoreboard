package com.example.scoreboard.exception;

/**
 * Stable, machine-readable code of a {@link ScoreboardException}, e.g. to map to an HTTP status or a
 * translated message. Users may define their own codes for their {@link CustomScoreboardException}s.
 */
public interface ErrorCode {

    String name();
}
