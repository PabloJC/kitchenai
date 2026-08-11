# KitchenAI infrastructure

Everything that is not app code: how the repository is wired, what each workflow does, and
the traps that cost an afternoon if you meet them unprepared.

Architecture rules live in [`CLAUDE.md`](../CLAUDE.md) and are normative: they are read both
by whoever implements and by the automated reviewer.

---

## The life of a task

```
gh issue create                      template with plan   ──► card in Todo
git checkout -b feat/12-slug
gh pr create --fill                  body with Closes #12 ──► In progress
                                     Claude reviews       ──► In review if approved
gh pr edit --add-label ready-to-merge                     ──► auto merge ──► Done
```

None of this needs manual intervention beyond writing the issue and adding the label.

---

## Workflows

| File | What it does |
|---|---|
| `ci.yml` | Lint, detekt, JVM tests, Android APK and iOS framework. Aggregated into the **`CI passed`** check |
| `ai-code-review.yml` | Claude reviews the diff against `CLAUDE.md`, comments inline and emits a verdict. **`Claude review`** check |
| `auto-merge.yml` | With the `ready-to-merge` label and everything green, squash-merges and deletes the branch |
| `project-sync.yml` | Moves the board card according to what happens to the issue and its PR |

`CI passed` and `Claude review` are the only two required checks on `main`. That way jobs
can be added or removed without touching the protected branch configuration.

---

## Setting it up from scratch

### Secrets

```bash
gh secret set GOOGLE_SERVICES_JSON          # base64 of the Android file
gh secret set GOOGLE_SERVICE_INFO_PLIST     # base64 of the iOS file
gh secret set CLAUDE_CODE_OAUTH_TOKEN       # claude setup-token
gh secret set PROJECT_TOKEN                 # classic token, see below
```

You also need the **Claude GitHub App** (<https://github.com/apps/claude>) installed on the
repository: the action exchanges an OIDC token for an app token, and without the app it
returns 401 before ever reaching Anthropic.

Firebase config files are **never committed**; CI restores them from secrets. Mind the path:
the iOS one lives at `iosApp/GoogleService-Info.plist`, not `iosApp/iosApp/`.

### Labels

```bash
gh label create ready-to-merge     --color 0E8A16 --description "Task finished: merge once checks are green"
gh label create skip-ai-review     --color FBCA04 --description "Skip the AI review"
gh label create ai-review:approved --color 0E8A16 --description "Approved by the AI reviewer"
gh label create task               --color 1D76DB --description "Unit of work with a development plan"
```

### Project board

```bash
gh auth refresh -s project,read:project
gh project create --owner @me --title KitchenAI
gh project link <number> --owner @me --repo <owner>/<repo>
gh variable set PROJECT_NUMBER --body "<number>"
gh variable set PROJECT_OWNER  --body "<owner>"
```

The `Status` field must end up with exactly four options — `Todo`, `In progress`,
`In review`, `Done`. The API cannot edit the options of an existing single-select field, so
add `In review` by hand in *Status → Edit field*. Names are compared case-insensitively.

To check what the board actually has:

```bash
gh project field-list <number> --owner @me --format json \
  --jq '.fields[] | select(.name == "Status") | .options[].name'
```

### Branch protection

```bash
./scripts/setup-branch-protection.sh
```

---

## App Check

The Firebase API keys are public by construction: they travel inside every binary. The
Firestore rules answer *"is this user allowed?"*, never *"does this request come from our
app at all?"*. App Check answers the second question — **Play Integrity** on Android,
**App Attest** on iOS — and lets Firebase reject anything else before a rule is evaluated.

Where it is wired:

| Platform | File | Provider |
|---|---|---|
| Android | `androidApp/src/main/kotlin/.../KitchenAiApplication.kt` | `appCheckProviderFactory()` from `src/debug` or `src/release` |
| iOS | `iosApp/iosApp/iOSApp.swift` | `#if DEBUG` → debug provider, otherwise `AppAttestProviderFactory` |

On both platforms the provider is installed **before** anything else touches Firebase:
before `initKoin` on Android, before `FirebaseApp.configure()` on iOS. Installing it
afterwards lets the first requests — the authentication ones — leave unattested.

The debug artifact is not merely disabled in release, it is absent: `firebase-appcheck-debug`
comes in through `debugImplementation`, and the factory lives in a per-build-type source set.
On iOS the `#if DEBUG` branch does not exist in a Release binary.

### Getting a debug token

Attestation needs real hardware and a real install. The emulator, the simulator and CI have
neither, so they use the debug provider, which prints a token on first launch:

```bash
# Android
adb logcat -s DebugAppCheckProvider          # "Enter this debug secret into the allow list…"
```

On iOS the token appears in the Xcode console. If it does not, add `-FIRDebugEnabled` to the
scheme arguments.

Register it in *Firebase console → App Check → the app → ⋮ → Manage debug tokens*. The token
identifies **a device, not a person**: it is not worth a GitHub secret, but it does not belong
in the repository either — anyone holding it can talk to the backend from anywhere. Tokens are
revoked from that same screen; give each one a name that says whose machine it is.

CI does not need one today: it builds but does not run anything against Firebase. The day it
does, the token goes in as a secret and gets injected into the debug build.

### Enforcement

Enforcement is enabled per API — Firestore, Authentication, Storage — and it is the part that
bites. Turning it on before the *Verified requests* metric is essentially 100% locks out your
own app, and the failure does not look like a policy decision: the SDK returns permission
errors identical to a rules problem, with no mention of App Check anywhere.

The order is not negotiable:

1. Ship a build with the providers installed.
2. Leave enforcement **off** for at least 24 h.
3. Check *App Check → APIs* and read the verified-requests percentage per API.
4. Only enable enforcement once what is left unverified is old app versions you are willing
   to break.

It is reversible from the console and takes effect within minutes, so the recovery from a
premature enable is quick — provided you remember that App Check is what you switched.

---

## Traps

### The review workflow must be identical to the one on `main`

```
Warning: Skipping action due to workflow validation: Workflow validation failed.
```

Claude's action refuses to run if `ai-code-review.yml` differs from the version on the
default branch. The reasoning is sound — otherwise any PR could rewrite the workflow and run
whatever it liked with the repository secrets — but it has two consequences:

1. To test a change to that workflow you must land it on `main` first.
2. No PR that touches `.github/workflows/` will ever be reviewed.

And it **skips silently**: the step stays green and publishes no outputs at all. If an
action output comes back empty, look for `workflow validation` in that step's log before
debugging whatever consumes the output.

The consequence for a PR that edits that file is that the review cannot produce a verdict,
the `Claude review` check goes red and — being required — the PR is stuck. The way out is
the `skip-ai-review` label, but it has to be there **when the workflow runs**:
`ai-code-review.yml` triggers on `opened` and `synchronize`, not on `labeled`, so adding the
label afterwards changes nothing on its own.

```bash
gh pr create --fill --label skip-ai-review     # label it from the start
git commit --allow-empty -m "chore: re-run"    # or force a new run if you forgot
```

The `labeled` event is deliberately not a trigger: it would run — and pay for — a full
review every time any label is added, `ready-to-merge` included.

### A required check that never reports blocks just like a red one

That is why the review skip (the `skip-ai-review` label, drafts) does not live in the job's
`if:` but inside it: the job always runs and always reports, whether it reviews or not.

### The reviewer publishes; the action does not

In agent mode Claude has to call `gh pr comment` and
`mcp__github_inline_comment__create_inline_comment` itself. Those tools belong in
`--allowedTools` inside `claude_args`; the `settings` block does not always reach the SDK,
and the requests end up as permission denials.

`track_progress` is deliberately off: it produces a tracking comment with tickable
checkboxes, and a review is not a to-do list.

### The Claude token fails in two different ways

| Symptom | Cause |
|---|---|
| `401 Invalid bearer token`, retried ten times | token well-formed but rejected |
| `Header 'Authorization' has invalid value`, fails in <100 ms | illegal character: newline or space |

To store any token without corrupting it, without the clipboard and without leaving it in
your shell history:

```bash
cat > /tmp/tok            # paste, Enter, Ctrl-D
tr -d '[:space:]' < /tmp/tok > /tmp/tok2
gh secret set NAME < /tmp/tok2 && rm -f /tmp/tok /tmp/tok2
```

`pbpaste` does not work: copying the command in order to paste it overwrites the clipboard.
Neither does `read -r`: it stops at the first newline and truncates the token.

### `enforce_admins` is not applied by the PUT

The branch protection `PUT` accepts `"enforce_admins": true`, answers 200 and leaves it at
`false`. It is enabled through its own dedicated endpoint, and it must be verified
afterwards: without that the protection looks configured while not applying to the
repository owner — the only person who was going to push anyway.
`setup-branch-protection.sh` does both and fails if it does not stick.

### `required_conversation_resolution` is incompatible with an automated reviewer

Every inline comment opens a thread, and a single unresolved thread leaves the PR `BLOCKED`
even when the verdict is `approve` and every check is green. GitHub answers *"the base branch
policy prohibits the merge"* without mentioning threads anywhere. It is disabled on purpose:
the gate is the `Claude review` check.

### The board needs a classic token with `project` and `read:org`

The Actions `GITHUB_TOKEN` cannot reach Projects, which lives outside the repository. Neither
can fine-grained tokens: their account permission list does not include `Projects`. It has to
be a classic token with `project` **and** `read:org` — without the second one `gh` cannot
resolve whether the owner is a user or an organisation and fails with `unknown owner type`.

Column names are compared case-insensitively, so `In Progress` and `In progress` both work.

---

## Structural decisions

### Why `:androidApp` exists

Since AGP 9, `com.android.application` and `com.android.library` are incompatible with the
Kotlin Multiplatform plugin in the same module. `:shared` and `:composeApp` use
`com.android.kotlin.multiplatform.library` with their configuration inside
`kotlin { androidLibrary { } }`, and `:androidApp` is left as the entry point holding
`MainActivity`, the `Application`, the manifest and the resources. No logic, no composables.

Two things that are not obvious:

- `:composeApp` needs `androidResources { enable = true }`. Without that line Compose
  resources are not packaged and the app crashes on launch
  ([CMP-9547](https://youtrack.jetbrains.com/issue/CMP-9547)).
- `:androidApp` does **not** apply `kotlin-android`: AGP 9 ships built-in Kotlin support and
  applying the plugin conflicts with it.

### GitLive pinned at 2.5.0

Release 2.6.0 is incomplete on Maven Central: `firebase-auth` and `firebase-firestore` were
published but the root `firebase-app` module was not, and the POMs declare it as a
dependency. Before bumping the version, check that it exists:

```bash
curl -s https://repo1.maven.org/maven2/dev/gitlive/firebase-app/maven-metadata.xml | grep '<version>'
```

The Firebase BOM goes in `androidMain` of `:shared` as `api`, not `implementation`: GitLive's
POMs declare the Google dependencies without versions and expect the consumer to supply the
BOM, and that constraint has to propagate to `:composeApp` and `:androidApp`.

### Kotlin/Native tests are disabled

The linker cannot find the iOS Firebase frameworks, which Xcode supplies through SPM. The
real fix is splitting `:shared` into `:domain` (pure Kotlin, testable everywhere) and
`:data`. Still pending.

---

## Known debt

- Restrict the iOS API key in Google Cloud Console to the app's bundle id.
- Enable App Check enforcement for Firestore, Auth and Storage once the verified-requests
  metric is clean. The code is in place; the switch is not flipped.
- Split `:shared` into `:domain` and `:data` to get iOS tests back.
- Re-add Analytics and Crashlytics **with their Gradle plugin**: without it the build id is
  missing and the app crashes on launch.
- Pin the exact test task names in CI instead of relying on `check`.
