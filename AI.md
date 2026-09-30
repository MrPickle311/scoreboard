# AI usage

## Summary

AI (Claude, in the claude.ai chat with a code sandbox) was used for design, reasoning, code generation and
testing. I drove the decisions; the AI proposed options, wrote the plan and the code, and ran the tests.

How the work went:

1. **Understanding the task.** The AI summarised the exercise PDF and listed the open questions it contains.
2. **Decisions.** I answered the open questions myself (one live match per team, no negative scores,
   incremental updates, id-based matches, thread safety, keeping finished matches) and added requirements:
   atomic, non-blocking updates for virtual threads, and a pluggable storage layer.
3. **Plan as a reviewable document.** The AI wrote an implementation plan as a shared doc. I reviewed it
   through inline comments, which reshaped the design: `Team`, `MatchId`, `Match`, `MatchStatus` and
   `ErrorCode` became user-implementable interfaces, errors became precise typed exceptions, the score API
   became per goal with cancellation, and `getFinishedMatches()` was chosen as the extra feature. All threads
   are recorded below.
4. **Implementation.** The AI implemented the plan commit by commit, test-first, compiling with all warnings
   as errors and running the JUnit 5 suite in its sandbox after every step (80 tests).
5. **Verification.** I run `mvn test` locally before submitting (the sandbox could not reach Maven Central,
   so it compiled with `javac` and ran JUnit's console launcher directly).

## Prompt history

1. Uploaded the exercise PDF: "Help me understand what is to do. Tell me if code execution is needed"
2. "Can you execute Java code here ?"
3. "Okay. Create git repo here and lets start scaffolding plan here"
4. "what is home and away ? Updates should be atomic, think about concurrent writes, it should not be blocking becase someone may use virtual threads. Library should have possibility to provide storage layer via interface. Answer: Team cannot play two matches at once, negative scores are not allowed, score update should be an incremenetal commit, match is identified via id, it should be threadsafe, finished matches should be kept for historical reasons. Plan here should be as an artifact i want to review IT."
5. "Make sure that my comments are visible in AI.md."
6. "Now proceed with implementation."

## Plan review comments
The implementation plan was written as a shared document and reviewed through inline comments. Every thread is recorded below verbatim, in the order it happened (2026-09-30). Each heading names the part of the plan the comment was attached to.
Plan document: https://claude.ai/code/artifact/72b953b1-b306-4c4d-8288-036d2e85db1d

### 1. On "Public API and domain model"

**Me:** do we need Team domain layer or its redundant ?

**Claude:** No separate Team layer is needed, but a small `Team` value record is worth it, not redundant: the trim + case-insensitive rule, the blank-name check and the alphabetical claim order would otherwise be repeated in Scoreboard, the repository and every future storage. It would just hold the display name plus a normalized key; `startMatch` can keep taking Strings. Want me to add it to the plan?

**Me:** Yes, add it. This should be interface that may be customized by library user. For example serialization or persistence may be customized

**Claude:** Done. `Team` is now a user-implementable interface (`name()` for display, `key()` for identity), and the library compares and claims teams only by `key()`, so a custom Team can carry its own persistence or serialization. `Team.of(String)` gives the trimmed, case-insensitive default, and `startMatch` accepts both Team and String. I also updated the package tree, the validation note and the commit plan to match.

### 2. On "MatchId"

**Me:** match id should also customizable interface because someone may use numbers as ids

**Claude:** Done. `MatchId` is now a user-implementable interface (default: a UUID record), created by the storage via a new `MatchRepository.nextId()`, so a custom repository can use numeric ids. The library compares ids only by equals/hashCode, so implementations must be value-based; I also updated the repository contract, the Decisions table and the package tree.

### 3. On "Match"

**Me:** match and match status should be also interfaces with possible customization

**Claude:** Done: Match and MatchStatus are now interfaces. Matches are created via MatchRepository.newMatch(...) (default Match.of) and changed only through their own withGoals/finished, so a custom type round-trips. The library reads status only via isLive(), so custom states like HALF_TIME or ABANDONED work. I also updated the repository (findLive), its contract, the package tree and a new interface code block.

### 4. On "Validation and errors"

**Me:** error handling should be handy for library user. It should tell exact errors

**Claude:** Done. Each failure now has its own exception with a message naming the exact values (team, match id, goals, status) plus typed getters for them. They all extend a sealed ScoreboardException with an ErrorCode, so you can switch over them exhaustively. Errors from custom storage are wrapped in RepositoryException, and MatchAlreadyFinishedException became MatchNotLiveException to cover custom statuses. The package tree is updated to match.

### 5. On "enum (ErrorCode)"

**Me:** use interfaces instead enums

**Claude:** Done: ErrorCode is now a public interface (String name()), and the eight library codes live in StandardErrorCode, an enum implementing it, the same pattern as MatchStatus / StandardMatchStatus. Custom repositories can add their own codes, and the package tree lists both. If you'd rather drop the default enum entirely (e.g. constants on the interface), say so.

### 6. On "sealed (ScoreboardException)"

**Me:** why sealed?

**Claude:** Sealed makes the list of errors the library can throw closed and known. Callers can `switch` over every case without a `default`, and the compiler flags any case they miss when a new exception is added. Custom storage still reports its own failures through `RepositoryException` (the original error is kept as its cause) or its own `ErrorCode`. If you'd rather let users subclass it, I can make the base non-sealed; you'd lose that exhaustiveness check.

**Me:** What if user may want to use it's custom exception derived from this Claas ?

**Claude:** Added an extension point: the sealed base now also permits `CustomScoreboardException`, a `non-sealed` abstract class users can extend freely, paired with their own `ErrorCode`. Callers keep an exhaustive `switch`, since all user exceptions fall under that one case (added to the example, the package tree and the repository note).

### 7. On "Open questions for review"

**Me:** we can decrement goal, but summary cannot go less than 0. 2. Per goal. 3. Case insensitive 5. No extra features if task does not require it

**Claude:** Done: the API is now per goal, scoreGoal(id, side) and cancelGoal(id, side), and a cancel is rejected if that side's score is already 0. Team names are case-insensitive and trimmed, and the retry bound stays unbounded with nothing extra added. I updated the Decisions table, API, concurrency steps, errors and tests to match, and left the extra-feature question open.

**Me:** Is extra feature required from task?

**Claude:** Per this plan, yes: it was written from the spec, and it says the spec wants one extra feature in its own commit (see the Commit plan). I can't open the PDF from here to check the exact wording, so please confirm it. If it's required, the cheapest option is getFinishedMatches(), because finished matches are already kept.

**Me:** Okay, then this extra feature is okay

**Claude:** Done: the extra feature is getFinishedMatches(). I added it to the API, named it in the Commit plan step, and ticked it off in Open questions.

**Note:** the spec does require the extra feature ("Add exactly one additional operation of your choice", with its own commit), which confirms the decision in thread 7.

## Issues the AI caught during implementation

Worth knowing for review, because each one is a subtle bug that tests alone might not reveal:

- **`ConcurrentHashMap.replace(key, old, new)` compares with `equals`.** A custom `Match` whose `equals`
  only compared ids could overwrite a newer version and lose an update. `InMemoryMatchRepository.replace`
  now checks the version inside `computeIfPresent`, so the check and the write are atomic and independent
  of `equals`.
- **Swallowed failures in concurrency tests.** Exceptions and failed assertions inside
  `ExecutorService.submit` tasks are captured in the `Future` and never reported. The test helper now calls
  `get()` on every future; a deliberately failing probe confirmed that worker failures fail the test.
- **Overflow in `totalScore()`.** Two scores near `Integer.MAX_VALUE` would overflow an `int` sum and corrupt
  the ordering, so `totalScore()` returns `long` (the plan had `int`).
- **Stale team claims.** Releasing a finished match's teams happens just after the finish is stored. A claim
  that still points at a non-live match is treated as stale and taken over, so a start right after a finish
  never fails spuriously.
- **Opposite simultaneous starts.** Claiming teams in key order prevents X–Y and Y–X from each taking one
  team and both failing (tested over 200 rounds).

## Artifacts that guided the implementation

- Implementation plan (shared doc, reviewed via the comments above):
  https://claude.ai/code/artifact/72b953b1-b306-4c4d-8288-036d2e85db1d
- Repository copy of the plan: [`docs/PLAN.md`](docs/PLAN.md)
- The exercise PDF (Sportradar "Coding Exercise – Data & Odds Platform")
