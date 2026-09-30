package com.example.scoreboard;

/** Default statuses. */
public enum StandardMatchStatus implements MatchStatus {
    IN_PROGRESS(true),
    FINISHED(false);

    private final boolean live;

    StandardMatchStatus(boolean live) {
        this.live = live;
    }

    @Override
    public boolean isLive() {
        return live;
    }
}
