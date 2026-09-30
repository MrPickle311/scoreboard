package com.example.scoreboard.exception;

/** Error codes of the exceptions thrown by the library itself. */
public enum StandardErrorCode implements ErrorCode {
    INVALID_TEAM,
    SAME_TEAM,
    INVALID_GOALS,
    TEAM_ALREADY_PLAYING,
    MATCH_NOT_FOUND,
    MATCH_NOT_LIVE,
    SCORE_OVERFLOW,
    REPOSITORY_FAILURE
}
