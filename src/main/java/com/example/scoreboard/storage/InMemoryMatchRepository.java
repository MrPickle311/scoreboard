package com.example.scoreboard.storage;

import com.example.scoreboard.Match;
import com.example.scoreboard.MatchId;
import com.example.scoreboard.Team;
import com.example.scoreboard.exception.TeamAlreadyPlayingException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default, thread-safe, non-blocking in-memory {@link MatchRepository}.
 *
 * <p>Matches live in one {@link ConcurrentHashMap}; a second map records which live match holds each team
 * (by key). Teams are claimed with {@code putIfAbsent} in key order, so two opposite starts (X–Y, Y–X) cannot
 * each take one team and both fail. A claim that still points at a match which is no longer live is stale and
 * can be taken over, which makes releasing a finished match's teams effectively atomic with the finish.
 *
 * <p>No locks are held by this class. {@code ConcurrentHashMap} uses a very short internal monitor per bin
 * that never waits or does I/O, so it does not harm virtual threads.
 */
public final class InMemoryMatchRepository implements MatchRepository {

    private final ConcurrentMap<MatchId, Match> matches = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MatchId> liveTeams = new ConcurrentHashMap<>();
    private final AtomicLong startSequence = new AtomicLong();

    @Override
    public MatchId nextId() {
        return MatchId.random();
    }

    @Override
    public long nextStartSequence() {
        return startSequence.incrementAndGet();
    }

    @Override
    public void insert(Match match) {
        Objects.requireNonNull(match, "match must not be null");
        MatchId id = match.id();
        if (!match.status().isLive()) {
            putNew(match);
            return;
        }
        Team first = match.homeTeam();
        Team second = match.awayTeam();
        if (first.key().compareTo(second.key()) > 0) {
            first = match.awayTeam();
            second = match.homeTeam();
        }
        claim(first, id);
        try {
            claim(second, id);
        } catch (RuntimeException e) {
            liveTeams.remove(first.key(), id);
            throw e;
        }
        try {
            putNew(match);
        } catch (RuntimeException e) {
            liveTeams.remove(first.key(), id);
            liveTeams.remove(second.key(), id);
            throw e;
        }
    }

    private void putNew(Match match) {
        if (matches.putIfAbsent(match.id(), match) != null) {
            throw new IllegalStateException("A match with id " + match.id() + " already exists");
        }
    }

    /** Lock-free claim of a team for a match; takes over stale claims left by finished matches. */
    private void claim(Team team, MatchId id) {
        String key = team.key();
        while (true) {
            MatchId holder = liveTeams.putIfAbsent(key, id);
            if (holder == null) {
                return;
            }
            Match holding = matches.get(holder);
            boolean stale = holding != null && !holding.status().isLive();
            if (!stale) {
                // holding == null: that match is being inserted right now, so the team is taken
                throw new TeamAlreadyPlayingException(team, holder);
            }
            if (liveTeams.replace(key, holder, id)) {
                return;
            }
            // someone else changed the claim in between; look again
        }
    }

    @Override
    public Optional<Match> findById(MatchId id) {
        return Optional.ofNullable(matches.get(id));
    }

    @Override
    public boolean replace(Match expected, Match updated) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(updated, "updated must not be null");
        if (!expected.id().equals(updated.id())) {
            throw new IllegalArgumentException("expected and updated must have the same id: "
                    + expected.id() + " vs " + updated.id());
        }
        boolean[] wasLive = new boolean[1];
        // the version check and the write happen atomically inside computeIfPresent;
        // the version (not equals) decides, so any Match implementation is handled correctly
        Match result = matches.computeIfPresent(expected.id(), (id, stored) -> {
            if (stored.version() != expected.version()) {
                return stored;
            }
            wasLive[0] = stored.status().isLive();
            return updated;
        });
        if (result != updated) {
            return false;
        }
        if (wasLive[0] && !updated.status().isLive()) {
            liveTeams.remove(updated.homeTeam().key(), updated.id());
            liveTeams.remove(updated.awayTeam().key(), updated.id());
        }
        return true;
    }

    @Override
    public List<Match> findLive() {
        return matches.values().stream()
                .filter(match -> match.status().isLive())
                .toList();
    }
}
