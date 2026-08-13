# KitchenAI MVP — issue backlog, execution waves and worktree plan

22 issues. Six waves. Everything inside a wave can run in parallel in its own git worktree,
driven by its own agent; a wave starts when the previous one is merged into `main`.

The issues exist: #31 to #52. Each carries its own context, development plan, file list,
acceptance criteria and diff budget, so this file is the map rather than the content — read
the issue before starting one.

---

## 1. Architectural decisions this backlog assumes

These are decided here so that 22 issues do not each re-decide them.

| Decision | Where it binds |
|---|---|
| **No contextual constant anywhere.** Diets, allergens, cuisines, regions, units, storage locations and ingredients exist only as documents in Firestore, referenced from code as opaque `TermRef`/`IngredientId`. No enum, no constant, no fallback list, not even in test fixtures. | #31, #35, #50 |
| **The client never writes prose.** The agent request carries identifiers and numbers. The instruction template, the model choice and the id→word resolution live server-side in the callable function. | #45, #51 |
| **The model proposes, the domain verifies.** `PantryMatcher` computes coverage over every suggestion; a `covered`/`missing` claim in a model response is never displayed. | #39, #45, #52 |
| **No provider API key in the binary.** The app calls a callable Cloud Function protected by the App Check work already in `main`. | #51 |
| **Everything user-owned lives under `users/{uid}`.** That is what keeps the Firestore rules a two-line owner check. | #38, #33 |
| **"Synchronised" means across one user's devices**, not shared with other people. Household sharing needs a top-level collection and a membership model — post-MVP. | #37, #43 |
| **No unit conversion in the MVP.** Mismatched units are `unverifiable`, never silently converted. | #31, #39 |
| **Observers stream data, not results.** Every `observeX` returns a bare `Flow<T>`; a listener that fails ends its stream and publishes on the port's `streamErrors(): Flow<AppError>`. Read-modify-write goes through a one-shot `getX(): AppResult<…>`, never the first emission of a listener. Decided in #68 after wave 2 shipped both shapes. | #39, #41, #42, #43, #44, #47 |

### Firestore layout (normative)

```
users/{uid}                                     profile
users/{uid}/pantry/{itemId}
users/{uid}/shoppingLists/{listId}
users/{uid}/shoppingLists/{listId}/items/{itemId}
users/{uid}/savedRecipes/{recipeId}
taxonomies/{taxonomyId}                         read-only catalogue
taxonomies/{taxonomyId}/terms/{termId}
ingredients/{ingredientId}                      read-only catalogue
recipes/{recipeId}                              read-only catalogue
```

---

## 2. The backlog

### Domain — `:shared/domain`, pure Kotlin

| Issue | Title | Blocked by | Wave |
|---|---|---|---|
| #31 | `feat(domain)`: domain primitives — identifiers, quantities and term references | — | 1 |
| #34 | `feat(domain)`: session port and authentication use cases | #31 | 2 |
| #35 | `feat(domain)`: dynamic vocabulary and user context model | #31 | 2 |
| #36 | `feat(domain)`: pantry model, port and use cases | #31 | 2 |
| #37 | `feat(domain)`: shopping list model, port and use cases | #31 | 2 |
| #39 | `feat(domain)`: recipe model and pantry matching | #31, #36 | 3 |
| #45 | `feat(domain)`: the agent seam — RecipeAgent, registry, orchestrator, SuggestRecipes | #35, #36, #39 | 4 |
| #46 | `feat(domain)`: cross-feature use cases — recipe to shopping list, and cooking a recipe | #36, #37, #39 | 4 |

### Data — `:shared/data`, Firebase

| Issue | Title | Blocked by | Wave |
|---|---|---|---|
| #33 | `chore(data)`: Firestore rules and indexes for the MVP collections | — | 1 |
| #38 | `feat(data)`: Firestore foundation — paths, error mapping, offline, serialisation | #31 | 2 |
| #40 | `feat(data)`: Firebase anonymous session adapter | #34, #38 | 3 |
| #41 | `feat(data)`: profile and taxonomy Firestore repositories | #35, #38 | 3 |
| #42 | `feat(data)`: pantry and ingredient-catalogue Firestore repositories | #36, #38 | 3 |
| #43 | `feat(data)`: shopping list Firestore repository | #37, #38 | 3 |
| #47 | `feat(data)`: recipe repository — catalogue reads and saved recipes | #38, #39 | 4 |
| #51 | `feat(data)`: LLM recipe agent over a callable Cloud Function | #45, #47 | 5 |

### Presentation / UI — `:composeApp`

| Issue | Title | Blocked by | Wave |
|---|---|---|---|
| #32 | `feat(ui)`: design system — tokens, states and shared components | — | 1 |
| #44 | `feat(ui)`: app shell, navigation graph and session gate | #32, #34, #35, #36 | 3 |
| #48 | `feat(ui)`: pantry screen | #32, #36, #42, #44 | 4 |
| #49 | `feat(ui)`: shopping list screen | #32, #37, #43, #44 | 4 |
| #50 | `feat(ui)`: profile and preferences screen | #32, #35, #41, #44 | 4 |
| #52 | `feat(ui)`: recipe suggestions and recipe detail | #32, #44, #45, #46, #47, #51 | 6 |

---

## 3. Execution waves

```
wave 1   #31 ── #32 ── #33                       3 agents
wave 2   #34 ── #35 ── #36 ── #37 ── #38         5 agents
wave 3   #39 ── #40 ── #41 ── #42 ── #43 ── #44  6 agents
wave 4   #45 ── #46 ── #47 ── #48 ── #49 ── #50  6 agents
wave 5   #51                                     1 agent
wave 6   #52                                     1 agent
```

Two ordering rules that are not visible from the dependency graph:

- **#38 is the gate for wave 3.** Five repositories are written against its paths, its error
  mapper and its serialisation conventions. If wave 2 has to be staggered, merge #38 first.
- **#44 must merge before wave 4.** All four screen issues add a route to the nav host it
  creates, and all four consume the `LabelResolver` it provides.

Waves 5 and 6 are single-issue by necessity: the agent adapter needs the seam, and the
suggestions UI needs the adapter. Nothing usefully parallelises there.

---

## 4. Worktree setup

One worktree per issue, all from the same clone:

```bash
git worktree add ../kai-31 -b feat/31-domain-primitives origin/main
```

Wave 1, three agents. Note #33 is a `chore/` branch, not `feat/` — each issue states its own
branch name:

```bash
git worktree add ../kai-31 -b feat/31-domain-primitives  origin/main
git worktree add ../kai-32 -b feat/32-design-system      origin/main
git worktree add ../kai-33 -b chore/33-firestore-rules-mvp origin/main
```

After a wave merges, drop its worktrees and branch the next one from the new `main`:

```bash
git worktree remove ../kai-31 && git branch -d feat/31-domain-primitives
```

Two things to hold to, both of which cost more to fix than to follow:

- **Every worktree branches from `origin/main`, never from a sibling worktree.** A branch on
  top of an unmerged branch turns one review into two.
- **Rebase on `main` at the start of every wave.** The shared-file conflicts below are all
  one-liners if you rebase, and all three-way merges if you do not.

Gradle note: parallel worktrees share `~/.gradle`. Concurrent builds are safe (Gradle locks
the caches), but a Kotlin/Native link across six worktrees at once will saturate the machine.
Stagger the builds, or give each worktree `GRADLE_USER_HOME` of its own if the machine has
the disk.

### Build traps

Found the hard way in wave 1. Both cost a failed build before they cost anything else.

- **No commas in backticked test names.** `compileTestKotlinIosSimulatorArm64` runs even
  though the Kotlin/Native test targets are disabled, and it fails the build with
  `Name contains illegal characters: ","`. Applies to every `commonTest` source set, so it
  applies to every issue in this backlog.
- **`rememberSwipeToDismissBoxState(confirmValueChange = …)` is deprecated** in the Compose
  version pinned here. React to `state.currentValue` in a `LaunchedEffect` instead — see
  `composeApp/.../designsystem/component/SwipeToDismissRow.kt` from #32.
- **`compose.components.uiToolingPreview` is deprecated** in Compose Multiplatform 1.11 and
  there is no `compose.uiToolingPreview` accessor. `@Preview` comes from the
  `org.jetbrains.compose.ui:ui-tooling-preview` coordinates via the version catalog, in an
  `androidMain` dependency block. #32 added both entries; #44 inherits them.

detekt has been analysing this project only since #67 — before that the task ran against no
source at all and every branch passed it for free. These are the rules that have blocked a
branch since, with what each one cost:

- **`TooManyFunctions`, 11 on interfaces.** Forced `ShoppingListPort` to be split in two (#73)
  once every observer gained its own keyed error stream. That split was right, but it arrived
  as a build failure rather than as a design decision.
- **`LongParameterList`, 8 constructor parameters.** Two screens hit it with six use cases plus
  a clock and a dispatcher. The answer was grouping collaborators by role — what the screen
  reads and what it writes — not raising the threshold and not dropping the dispatcher, which
  one branch did and quietly moved all its work onto the main thread.
- **`ReturnCount`, 4** — it counts non-local returns through inline lambdas, so a chain of
  `getOrElse { return … }` trips it at what looks like three.
- **`DestructuringDeclarationWithTooManyEntries`, 3.**

Never raise a threshold to make a branch pass. Restructure, or say so in the pull request.

#71 lifted detekt to 2.x, which changed how those ceilings are spelled — `threshold` became
`allowedLines`, `allowedFunctionParameters`, `allowedConstructorParameters`. The numbers did not
move. Two things did: typed rules work now, so a `println` or a hardcoded `Dispatchers.IO` in
`commonMain` fails the build, and the analysis runs under **`detektAll`** — the bare `detekt`
task reads `src/main`, which no module here has.

Wave 2 added one more, and it is the reason every ceiling in §6 moved:

- **`ktlint_official` rejects a multi-parameter signature on one line.** A port method or an
  `invoke` with two parameters costs four to six lines, not one, and `ktlintFormat` will not
  collapse it back. Combined with one public type per file — a `package` line and an import
  block repeated per file — the mechanical floor of a domain issue is several hundred lines
  before any logic. Three of the five wave-2 branches broke their budget on that alone, and
  each of their agents spent a round trimming tests before reporting the overrun. Do not trim
  tests to fit: say so in the pull request.

---

## 5. Shared-file collision matrix

Issues in the same wave that touch the same file, and the mitigation:

| File | Touched by | Mitigation |
|---|---|---|
| `shared/di/SharedModule.kt` | almost every domain and data issue | Each issue creates its own `di/<Feature>Module.kt` and appends **one line** to the `sharedModules` list, in alphabetical order. The list is one module per line with a trailing comma since #38 — wave 2 cost four rebase rounds because it was a single `listOf(…)` line and five branches appended to it. Conflicts are one line, but only if two modules are not alphabetically adjacent. |
| `composeApp/di/PresentationModule.kt` | #44 (rewrites), #48/#49/#52/#50 (one line each) | Same pattern: per-feature module file, one appended line. |
| `composeApp/navigation/KitchenAiNavHost.kt` | #44 (creates), #48/#49/#50 (one line each), #52 (two) | Keep each route entry to a single line. |
| `firebase/firestore.indexes.json` | #33 (wave 1, structure), #42 and #43 (wave 3, one index each) | #33 lands first; the two wave-3 issues append sibling objects. |
| `gradle/libs.versions.toml` | #32 (preview, wave 1), #44 (navigation, wave 3), #51 (functions, wave 5) | Different waves — no overlap. Each appends one entry. |
| `shared/build.gradle.kts` | #51 only | — |
| `composeApp/build.gradle.kts` | #32 (wave 1, `androidMain` block), #44 (wave 3) | Different waves. #44 rebases onto the block #32 added. |

Every other file in the backlog is owned by exactly one issue. That is not luck; it is why
each issue lists the files it will touch.

---

## 6. Keeping the review inside its token budget

The reviewing agent reads the diff, the issue and the conventions. Three levers keep that
affordable, and every issue body applies all three:

1. **A declared diff budget** — a target and a hard ceiling in files and lines. Ceilings run
   from ~400 lines (#40) to ~1190 (#52). A branch that breaches its ceiling is not one issue any
   more; split it and open a follow-up rather than pushing a diff that gets skimmed.

   Those numbers are the second calibration. The first was set before anything had been
   written and did not account for the ktlint signature rule in §4: it charged a port method
   one line where the formatter charges five. Every ceiling from #39 to #52 was raised by
   roughly a quarter, plus two files, once wave 2 had measured the real cost. If a branch
   still does not fit, the honest move is to report the overrun in the pull request — a use
   case shipped without its test is a blocking review finding, so the test is never what
   gives.
2. **The implementation resolved in advance.** Type signatures, the wire contract, the error
   mappings and the named test cases are all in the issue body. The review is then a
   comparison against a stated design, not a reconstruction of one — which is the expensive
   mode.
3. **Attention pointed at the right file.** The largest issues (#45, #51, #52) say in their diff
   budget which file is worth the reviewer's context: the orchestrator, the response
   validator, the ViewModel guard. Repeat that line in the PR body.

A fourth lever, for the human: keep waves narrow enough that reviews do not queue. Six
concurrent PRs is the practical ceiling for one reviewer, human or agent, which is why waves
3 and 4 are capped at six.

---

## 7. What this backlog deliberately leaves out

Not oversights — scope decisions, each with a reason:

- **Household / multi-user sharing.** Needs a top-level collection and a membership model;
  it changes the rules and the sync story, not just a query.
- **The Cloud Function itself.** Backend work outside this repository's Kotlin modules. #51
  states the contract it assumes and fails loudly if the response does not match.
- **Unit conversion**, **barcode and photo input**, **meal planning**, **streaming agent
  responses**, **a second agent implementation**. The seam for the last one is built; the
  second implementation is a Koin line whenever it is wanted.
- **A Compose UI / screenshot test harness.** Considered as a wave-1 issue and rejected: a
  common Compose UI test cannot run on iOS here at all (the Kotlin/Native test targets are
  disabled in both modules because the linker cannot find the Firebase frameworks), so it
  would buy Android-only coverage at the price of Robolectric or an emulator in CI. #32
  instead extracts its two pieces of real logic — amount parsing and coverage clamping — into
  pure functions and tests them with the setup that already exists. Every ViewModel in the
  backlog is tested regardless; that is where UI logic risk actually lives.

---

## 8. What waves 3 and 4 got wrong

Every screen in wave 4 needed between three and six review rounds, and they lost them to the
same handful of mistakes rather than to anything specific to each screen. None of these were in
any acceptance criterion, which is why they all shipped and were caught late.

### The states nobody enumerated

Acceptance criteria described what a screen shows when everything works. Every finding of
substance was about what it shows when something half-works, and there are more of those states
than anyone wrote down:

- **One error per source, cleared by that source.** A screen with several listeners and a single
  `error: String?` produces three separate bugs, and all three shipped: a recovering listener
  clears a banner belonging to one that is still broken; the failing listener's own recovery
  never clears anything, so a transient blip leaves a permanent message; and a rejected write is
  wiped by the next echo from an unrelated stream. Keep one slot per listener plus one for
  writes, clear each on its own recovery, and let the write speak first — it is the thing the
  user just did.
- **Three states, not two.** *Has not answered yet*, *answered and empty* and *failed* are three
  different screens. `isLoading` means "this particular listener has not answered", not
  "something somewhere is pending". Telling a user their pantry is empty when the read was
  denied reached a device twice.
- **Clear a banner where the listener speaks, not where the state is projected.** Re-emitting an
  unchanged value leaves a `combine` over `StateFlow`s silent, and a listener that recovered
  with identical data still recovered.

### Derive the state, never assemble it

Several collectors each calling a `render()` that reads five fields and writes the whole state
will publish combinations that never held at any instant — whichever finishes last wins with a
stale view of the rest. Build the UI state from a `combine` of its sources with a pure
projection. `ShoppingViewModel` is the worked example, and it got there by being rewritten
after four rounds of patching symptoms.

### Fakes that cannot fail hide the branches that matter

Four separate bugs hid behind a fake that could not do what a real one does:

| The fake | What it hid |
|---|---|
| Writes that always succeeded | Every write-failure branch, untested |
| A catalogue port with no error stream | A broken catalogue emptying a screen |
| A listener emitting an empty list on subscribe | "Not answered yet" being indistinguishable from "empty" |
| A `saveCount` incremented without synchronisation | A double write under a real dispatcher |

A fake that only models the happy path is a test that only tests the happy path, however many
cases it has.

### The file lists forget the thin tests

Four wave-2 agents independently added test files the issue's own file list omitted, because
CLAUDE.md makes an untested use case a blocking finding and the lists skip the one-line
observers. Assume any issue's file list is short by the tests for its thinnest use cases, and
count them in the budget rather than treating them as an overrun.

### Verification that cannot be done says so

"Runs on Android and iOS; screenshots in the PR" appears in four issues. Until #90 the iOS app
could not launch at all, and it still cannot reach a signed-in state without a development team
that `docs/infra.md` records as absent. A criterion nobody can meet is worse than no criterion:
it either blocks a correct branch or gets waved through, and both teach the wrong thing. State
what was verified, on which platform, and what was not.
