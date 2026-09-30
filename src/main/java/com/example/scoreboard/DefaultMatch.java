package com.example.scoreboard;

import java.util.Objects;

/** Default {@link Match}. */
record DefaultMatch(
        MatchId id,
        Team homeTeam,
        Team awayTeam,
        int homeScore,
        int awayScore,
        MatchStatus status,
        long startSequence,
        long version) implements Match {

    DefaultMatch {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(homeTeam, "homeTeam must not be null");
        Objects.requireNonNull(awayTeam, "awayTeam must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException("scores must not be negative: " + homeScore + "-" + awayScore);
        }
    }

    @Override
    public Match withGoal(Side side, int delta) {
        return side == Side.HOME
                ? new DefaultMatch(id, homeTeam, awayTeam, homeScore + delta, awayScore, status, startSequence, version + 1)
                : new DefaultMatch(id, homeTeam, awayTeam, homeScore, awayScore + delta, status, startSequence, version + 1);
    }

    @Override
    public Match finished() {
        return new DefaultMatch(id, homeTeam, awayTeam, homeScore, awayScore,
                StandardMatchStatus.FINISHED, startSequence, version + 1);
    }

    @Override
    public String toString() {
        return homeTeam.name() + " " + homeScore + " - " + awayTeam.name() + " " + awayScore;
    }
}
