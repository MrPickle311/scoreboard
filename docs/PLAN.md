# Live Scoreboard – Implementation Plan

This is the repository copy of the plan that guided the implementation. It was written with AI assistance
and reviewed through inline comments (see `AI.md`). Live version:
https://claude.ai/code/artifact/72b953b1-b306-4c4d-8288-036d2e85db1d

## Glossary

The **home team** is listed first and the **away team** second, as in "Mexico 0 – Canada 5".
At a World Cup most venues are neutral, so home/away only fixes display order and which score is whose.

## Decisions

| Question | Decision | How it is enforced |
| --- | --- | --- |
| Can a team play two matches at once? | No | Storage atomically rejects a start if either team is in a live match |
| Negative scores? | Not allowed | A goal can be cancelled, but a side's score never goes below 0 |
| Absolute or incremental update? | Incremental, one goal per call | `scoreGoal(id, side)` adds one goal; `cancelGoal(id, side)` removes one |
| How is a match identified? | By a storage-generated `MatchId` | `startMatch` returns it; every later call takes it |
| Thread-safe? | Yes, atomic and non-blocking | Immutable snapshots + compare-and-set with retry; no locks held by the library |
| Finished matches? | Kept as history | Status becomes non-live; excluded from the summary |
| Storage | Pluggable | `MatchRepository` interface; in-memory default |
| Java version | 21 | Virtual threads are a Java 21 feature |
| Team name matching | Case-insensitive, trimmed | `Team.of(...)` keys a team by its trimmed, lower-cased name |
| Bound on CAS retries | None | Each retry means another write succeeded (lock-free progress) |
| Extra feature | `getFinishedMatches()` | Queries the kept history |

## Public API

```java
public final class Scoreboard {
    public Scoreboard()                                  // in-memory storage
    public Scoreboard(MatchRepository repository)        // custom storage

    public MatchId startMatch(Team homeTeam, Team awayTeam);
    public MatchId startMatch(String homeTeam, String awayTeam); // = Team.of(...)
    public Match scoreGoal(MatchId id, Side side);       // +1 goal
    public Match cancelGoal(MatchId id, Side side);      // -1 goal, never below 0
    public Match finishMatch(MatchId id);
    public List<Match> getSummary();                     // live only, ordered
    public List<Match> getFinishedMatches();             // extra: kept history
}
```

`Team`, `MatchId`, `Match`, `MatchStatus` and `ErrorCode` are interfaces users can implement; defaults are
`Team.of`, a UUID-based id, `Match.of`, `StandardMatchStatus` and `StandardErrorCode`. The library compares
teams only by `key()`, ids only by `equals`/`hashCode`, and reads status only through `isLive()`.

Summary order: total score descending, then `startSequence` descending. The sequence is a counter issued by
the repository (never a timestamp, which can tie).

## Concurrency

Goal update: read snapshot → reject if not live / cancel below 0 → build new snapshot (`version + 1`) →
`repository.replace(expected, updated)` (CAS on version) → on failure retry from the read.
Start: the repository claims both teams atomically, in key order. Finish: same CAS loop to a non-live status,
then teams are released. No `synchronized`, no locks, no waiting in library code (virtual-thread friendly).

## Storage contract (`MatchRepository`)

- `insert` fails with `TeamAlreadyPlayingException` if either team is in a live match, atomically.
- `replace` succeeds only if the stored version equals `expected.version()`; a non-live `updated` frees both teams.
- `nextStartSequence` is strictly increasing; `nextId` never repeats.
- No method blocks waiting on another caller.

## Errors

Each failure has its own exception with exact values in the message and typed getters. All extend the sealed
`ScoreboardException` (with an `ErrorCode`); users extend the non-sealed `CustomScoreboardException`.
Failures or contract breaches in user-supplied code are wrapped in `RepositoryException`. Null arguments throw
`NullPointerException` naming the parameter.

## Tests

Unit tests per operation, the spec example as an acceptance test, an abstract repository contract test,
and virtual-thread concurrency tests asserting final state (no lost updates, exactly one start per team).
