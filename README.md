# Live Football World Cup Scoreboard

A small Java library that keeps a live scoreboard of football matches. It is thread-safe, never blocks
(safe to use from virtual threads), and lets you plug in your own storage and domain types.

## Build & test

```bash
mvn test
```

Requires Java 21+ and Maven 3.8+. No runtime dependencies; JUnit 5 is test-only.

## Usage

```java
Scoreboard board = new Scoreboard();                 // in-memory storage

MatchId id = board.startMatch("Mexico", "Canada");   // 0 - 0
board.scoreGoal(id, Side.AWAY);                      // Mexico 0 - Canada 1
board.cancelGoal(id, Side.AWAY);                     // VAR: Mexico 0 - Canada 0
board.finishMatch(id);                               // leaves the summary, kept as history

List<Match> live = board.getSummary();               // ordered live matches
List<Match> results = board.getFinishedMatches();    // extra feature: finished matches
```

Custom storage: `new Scoreboard(myRepository)` where `myRepository` implements `MatchRepository`.

## Operations

| Operation | Behaviour |
| --- | --- |
| `startMatch(home, away)` | Creates a live match at 0–0 and returns its `MatchId` |
| `scoreGoal(id, side)` | Adds one goal to the home or away side |
| `cancelGoal(id, side)` | Removes one goal (e.g. VAR); a score never goes below 0 |
| `finishMatch(id)` | Ends the match; it leaves the summary but is kept |
| `getSummary()` | Live matches by total score descending; ties: most recently started first |
| `getFinishedMatches()` | **Extra feature**: finished matches, most recently started first |

## Assumptions

- **Home and away**: the home team is simply the one listed first ("Mexico 0 – Canada 5"). At a World Cup
  venues are mostly neutral, so it only fixes display order.
- **A team plays at most one live match at a time.** Starting a second one is rejected.
- **Scores are never negative.** A goal can be cancelled, but not below 0.
- **Score updates are incremental, one goal per call**, matching how goals happen in a live feed.
- **Matches are identified by an id** issued at start, not by team names, because the same pairing can play
  again (a rematch after the first match finished).
- **Team names are trimmed and case-insensitive**: "Mexico" and " mexico " are the same team; the original
  spelling is kept for display.
- **Finished matches are kept** for historical reasons and never appear in the summary.
- **"Most recently started"** means start order as issued by the storage, not wall-clock time.
- The library is a component inside a larger system: no UI, no persistence of its own, no I/O.

## Design and reasoning

**Entry point.** `Scoreboard` is the only class most callers need. Everything it returns is an immutable
`Match` snapshot, so callers cannot change state behind its back.

**Concurrency: optimistic, lock-free.** Every change follows the same loop: read the current snapshot,
validate it, build a new snapshot with `version + 1`, and store it with a compare-and-set on the version
(`MatchRepository.replace`). If another write got there first, the loop re-reads and tries again. This gives:

- **No lost updates.** 10,000 concurrent goals end at exactly 10,000 (tested). This is also why updates are
  incremental: with absolute scores ("set to 2–1"), the slower of two writers would silently overwrite the other.
- **No blocking.** The library has no `synchronized`, no locks and never waits, so it suits virtual threads
  (on Java 21, blocking inside `synchronized` pins the carrier thread).
- **Progress.** A retry only happens because another write succeeded, so the system as a whole always
  moves forward.

**One live match per team, atomically.** The in-memory repository claims both teams with `putIfAbsent`
before storing the match. Teams are claimed in key order, so two opposite starts (X–Y and Y–X) cannot each
take one team and both fail. A claim left behind by a finished match is recognised as stale and taken over,
so a finish and a new start with the same team never conflict spuriously.

**Summary order.** Total score descending, then start sequence descending. The sequence is a counter from
the repository, not a timestamp: two matches can start in the same millisecond but never get the same number,
and a shared database can issue it for several application instances.

**Pluggable storage.** `MatchRepository` exposes exactly the atomic primitives the design needs
(`insert` with the team rule, version-checked `replace`, id and sequence generation, snapshot queries).
Its Javadoc states the contract, and the abstract `MatchRepositoryContractTest` lets any implementation prove
it follows it. A database could meet it with a unique partial index on live teams,
`UPDATE … WHERE id = ? AND version = ?` and a sequence.

**Customisable domain types.** `Team`, `MatchId`, `Match`, `MatchStatus` and `ErrorCode` are interfaces with
defaults (`Team.of`, UUID ids, `Match.of`, `StandardMatchStatus`, `StandardErrorCode`). The library only ever
compares teams by `key()`, ids by `equals`, and reads status through `isLive()`, so users can bring numeric
ids, persistence entities, or statuses such as `HALF_TIME` (live) and `ABANDONED` (not live).
Because user code runs inside the library, the library checks what it returns (new instance, same id,
`version + 1`, correct score change) and reports breaches precisely.

**Errors.** Each failure has its own exception with the exact values in the message and typed getters
(e.g. `TeamAlreadyPlayingException.liveMatchId()`). All extend a sealed `ScoreboardException` with an
`ErrorCode`, so callers can catch one type or `switch` exhaustively. Users add their own errors by extending
the non-sealed `CustomScoreboardException`. Anything else thrown by user-supplied code is wrapped in
`RepositoryException` with the original as the cause. Null arguments throw `NullPointerException` naming the
parameter, because that is a programming bug rather than a data error.

## Trade-offs

| Choice | Benefit | Cost |
| --- | --- | --- |
| Lock-free CAS instead of locks | Never blocks, virtual-thread friendly, unrelated matches never contend | Under extreme contention on one match, threads retry; retries are unbounded (no retry limit was required) |
| Per-match CAS instead of one `AtomicReference` to an immutable map | A goal costs O(1), not a copy of the whole board | The summary is a weakly consistent snapshot: it may miss a write in flight at that instant |
| Incremental, one goal per call | No lost updates, matches a live goal feed | Setting a score directly (e.g. loading 10–2) takes several calls |
| Cancel allowed, never below 0 | Supports VAR corrections | A cancel cannot target a specific goal, only the count |
| Finished matches kept | History for `getFinishedMatches()` | Memory grows with every match; for long-running use, plug in real storage |
| Interfaces for domain types | Users can plug in their own ids, entities and statuses | More types to learn, and the library must validate user-supplied results |
| Sealed exception base | Exhaustive `switch` for callers | Users extend only through `CustomScoreboardException` |
| Start order from a counter, not time | Deterministic ties | Order reflects when the start was issued, not a kick-off time |
| Finished matches ordered by start | No extra field needed | Not ordered by finish time, which is not tracked |

## Additional feature: `getFinishedMatches()`

Returns matches that are no longer live, with their final scores, most recently started first.

**Why this one:** finished matches must be kept for historical reasons anyway, but without a way to read them
that history is invisible to users of the library. Exposing it is the most useful thing the kept data enables
(showing final results next to the live board), and it costs one repository query and no new state. It was
added in its own commit (`Add getFinishedMatches(): ...`).

## Project layout

```text
com.example.scoreboard           Scoreboard, Team, MatchId, Match, MatchStatus, StandardMatchStatus, Side
com.example.scoreboard.exception ScoreboardException hierarchy, ErrorCode, StandardErrorCode
com.example.scoreboard.storage   MatchRepository (contract), InMemoryMatchRepository
docs/PLAN.md                     implementation plan that guided the work
```

## Tests

80 tests: unit tests per operation (including every error message), the specification example as an
acceptance test, the reusable repository contract test, tests with custom ids, matches, statuses, repositories
and exceptions, and concurrency tests on virtual threads that assert final state (no lost goals, exactly one
start per team, goals racing a finish are either counted or rejected).
