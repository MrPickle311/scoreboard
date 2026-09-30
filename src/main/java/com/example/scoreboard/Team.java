package com.example.scoreboard;

/**
 * A team taking part in a match.
 *
 * <p>Users may provide their own implementation, e.g. a persistence entity or a type carrying a FIFA code.
 * The library identifies teams <b>only</b> by {@link #key()} and never uses {@code equals}.
 */
public interface Team {

    /** Display spelling, e.g. {@code "Mexico"}. */
    String name();

    /**
     * Identity of the team: two teams are the same exactly when their keys are equal.
     * Must be non-blank and stable for the team's lifetime.
     */
    String key();

    /**
     * Default team: the name is trimmed and the key is the lower-cased trimmed name,
     * so {@code "Mexico"} and {@code " mexico "} are the same team.
     *
     * @throws NullPointerException if {@code name} is null
     * @throws com.example.scoreboard.exception.InvalidTeamException if {@code name} is blank
     */
    static Team of(String name) {
        return SimpleTeam.of(name);
    }
}
