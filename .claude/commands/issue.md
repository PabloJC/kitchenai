---
description: Implement a GitHub issue end to end, from branch to pull request
argument-hint: <issue number>
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, Task
---

Implement issue #$1.

`CLAUDE.md` is normative — the architecture rules, the dependency rule, the test
requirements and the review criteria all live there, and the same file is what the
automated reviewer will judge the pull request against. Read it before you touch anything.

## 1. Read the issue

```bash
gh issue view $1 --json number,title,body,labels
```

Extract from it: the **type** (`feat` / `fix` / `chore` / `docs`), the **development plan**,
the **files affected**, the **acceptance criteria** and any `Blocked by #N`.

Stop and say so if:

- a blocking dependency is still open,
- the plan contradicts `CLAUDE.md`,
- the plan cannot be carried out as written.

That last one is not a reason to abandon it. If a step is wrong, do the right thing and say
in the pull request body which step you deviated from and why — a reviewer who sees an
unexplained deviation reads it as the plan being ignored.

## 2. Branch

```bash
git switch main && git pull
git switch -c <type>/$1-<slug>
```

## 3. Implement

Follow the plan step by step. Touch nothing outside the *Files affected* list without
saying so.

Non-negotiable, because each one is a blocking finding in review:

- `shared/domain` stays pure Kotlin. No Firebase, Android, iOS, Compose or Ktor.
- Nothing crosses a layer boundary as an exception: it travels in `AppResult<T>`.
- No hardcoded `Dispatchers.IO` — inject `DispatcherProvider`.
- Every new or modified use case gets a test in `shared/src/commonTest`.
- No secret, key or Firebase config file in the diff. Check with `git diff --stat` before
  committing, not after.

## 4. Verify — before opening anything

Run what CI runs, in the order CI runs it. A pull request that fails a check you could have
run locally wastes a full review cycle:

```bash
./gradlew ktlintCheck detekt
./gradlew :shared:check :composeApp:check
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

If the issue touches `iosApp/`, also build the app itself — the Gradle task only links the
framework and will not catch a Swift error:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Then re-read the acceptance criteria one by one against what you actually built. Any you
cannot satisfy from a diff — console work, a physical device — belongs in the pull request
body as an explicit unchecked box with the reason, not silently dropped.

## 5. Commit and open the pull request

Conventional Commits, scoped to the layer: `feat(domain): ...`, `chore(infra): ...`. The
body explains *why*, not *what* — the diff already says what.

```bash
git push -u origin HEAD
gh pr create --fill-first --body-file <(...)
```

The body must contain `Closes #$1`, every deviation from the plan with its reason, and the
acceptance criteria as a checklist reflecting reality.

Do **not** merge, and do not add `ready-to-merge`. That label is Pablo's call once the
review is green; `auto-merge.yml` takes it from there and moves the card to Done.

## 6. Report back

Give the pull request URL, what deviated from the plan, what could not be verified locally,
and anything the issue asked for that only a human with console access can finish.
