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

---

## 5. Shared-file collision matrix

Issues in the same wave that touch the same file, and the mitigation:

| File | Touched by | Mitigation |
|---|---|---|
| `shared/di/SharedModule.kt` | almost every domain and data issue | Each issue creates its own `di/<Feature>Module.kt` and appends **one line** to the `sharedModules` list, in alphabetical order. Conflicts are one line. |
| `composeApp/di/PresentationModule.kt` | #44 (rewrites), #48/#49/#52/#50 (one line each) | Same pattern: per-feature module file, one appended line. |
| `composeApp/navigation/KitchenAiNavHost.kt` | #44 (creates), #48/#49/#50 (one line each), #52 (two) | Keep each route entry to a single line. |
| `firebase/firestore.indexes.json` | #33 (wave 1, structure), #42 and #43 (wave 3, one index each) | #33 lands first; the two wave-3 issues append sibling objects. |
| `gradle/libs.versions.toml` | #44 (navigation, wave 3), #51 (functions, wave 5) | Different waves — no overlap. |
| `shared/build.gradle.kts` | #51 only | — |
| `composeApp/build.gradle.kts` | #44 only | — |

Every other file in the backlog is owned by exactly one issue. That is not luck; it is why
each issue lists the files it will touch.

---

## 6. Keeping the review inside its token budget

The reviewing agent reads the diff, the issue and the conventions. Three levers keep that
affordable, and every issue body applies all three:

1. **A declared diff budget** — a target and a hard ceiling in files and lines. Ceilings run
   from ~300 lines (#40) to ~950 (#52). A branch that breaches its ceiling is not one issue any
   more; split it and open a follow-up rather than pushing a diff that gets skimmed.
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
