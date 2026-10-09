# Firebase configuration

| File | What it is |
|---|---|
| `firestore.rules` | The only security control between a client and the database. |
| `firestore.indexes.json` | Composite indexes and field overrides, deployed with the rules. |
| `storage.rules` | Storage rules: own-folder writes, capped in size. |
| `tests/` | Emulator tests for `firestore.rules`. |

`firebase.json` at the repository root points at all three and configures the emulator ports.

## Rules tests

They need Node 20+ and a JDK — the Firestore emulator is a Java process. From `firebase/tests`:

```bash
npm install
npm test
```

`npm test` starts the emulator, runs the tests against the rules file as committed and shuts
the emulator down. It uses the project id `demo-kitchenai-rules`: the `demo-` prefix is what
tells the emulator never to reach a real Firebase backend, so no credential, no secret and no
project of ours is involved. CI runs the same command.

Every rule needs a test. A rule asserted by reading is a rule that is wrong, and this is the
one part of the project that cannot be fixed in a follow-up: a permissive rule is exploitable
the moment it deploys.

Validating against the real project (`firebase deploy --only firestore:rules --dry-run`)
needs credentials and a project alias, so it is a local step for whoever holds them, not a CI
step.

### Convention: a rule that reads a document written in the same commit uses `getAfter()`

`get()` and `exists()` read the database as it was **before** the commit; `getAfter()` and
`existsAfter()` read it as it will be **after**. A rule on a document that lands together with the
document it reads — an invite created with its kitchen, as `createKitchen` does — must use the
`After` form, or production refuses the write (`isKitchenOwnerAfterWrite`, #222).

Two things keep this honest:

- **Write the test in the shape of the app's commit.** The invite tests used to seed the kitchen
  first and then create the invite, so they never exercised the case the app actually issues: both
  in one `writeBatch`. The tests under *kitchen invites* now do, and they fail if the rule goes
  back to `get()`. A rule test that seeds the document the rule reads has not tested the rule.
- **`get()` vs `getAfter()` on the emulator.** #222 and the comment in `firestore.rules` say the
  emulator lets `get()` see the same write. With the pinned `firebase-tools` it does not: an atomic
  create judged by `get()` is denied on the emulator too, which is what
  `exists() and existsAfter() inside one atomic write` pins with throwaway rules. So the bug was
  hidden by the test shape, not by an emulator difference. If a future emulator does diverge, that
  test goes red; the authority is still the deployed project, checked by `tools/smoke-rules.mjs`.

## Deploying

CI never deploys, so a merge changes nothing in the backend until someone runs this. On
2026-10-08 the deployed project was two months behind the code and every new user failed at first
launch; this is the order that fixed it. All commands name the project explicitly — there is no
default and no `firebase use` alias to forget.

```bash
firebase login
gcloud auth application-default login      # the seed and the cleanup use these credentials
npm --prefix tools install
npm --prefix functions install
```

1. **Rules and indexes.** Everything else reads and writes through them.

   ```bash
   firebase deploy --only firestore:rules,firestore:indexes --project <projectId>
   ```

   Indexes build in the background; a query that needs one fails until it is `READY`
   (*Console → Firestore → Indexes*).

2. **Seed the catalogues** (taxonomies, terms, ingredients). Idempotent, so run it whenever
   `seed/*.json` changed.

   ```bash
   node tools/seed.mjs --project <projectId>
   ```

3. **Functions** (`suggestRecipes`, `writeCatalogue`). They resolve against the catalogue the
   previous step wrote. The region is `FUNCTIONS_REGION` and must agree with `gradle.properties`
   and `iosApp/Configuration/Config.xcconfig`; see `functions/README.md`.

   ```bash
   firebase deploy --only functions --project <projectId>
   ```

4. **Smoke check** (below), before telling anyone it is live.

Storage rules (`firebase deploy --only storage`) are separate and only needed when
`storage.rules` changed.

### Post-deploy smoke check

`tools/smoke-rules.mjs` replays, against the deployed project, the writes the app makes: a new
owner creating a kitchen and its invite in one commit; a second user leaving their own kitchen and
joining the first in **one** commit; the owner removing them; their rejoin being **refused**; and
the owner regenerating the code atomically. It signs in two throwaway anonymous users, prints one
line per step and exits non-zero on the first unexpected result (1 for a result, 2 for bad
arguments).

```bash
KITCHENAI_PROJECT=<projectId> KITCHENAI_API_KEY=<webApiKey> node tools/smoke-rules.mjs
```

Or read the project, key and package from the local `google-services.json` (gitignored):

```bash
node tools/smoke-rules.mjs --config androidApp/google-services.json --cleanup
```

- **A restricted API key** (the one in `google-services.json` is restricted to the Android app)
  refuses a call from a laptop unless it identifies itself as the app. Pass the debug keystore's
  fingerprint, plus the package if `--config` is not used:
  `--cert-sha1 <sha1>` (or `KITCHENAI_CERT_SHA1`), `--android-package` (or
  `KITCHENAI_ANDROID_PACKAGE`). The fingerprint comes from
  `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`.
- **It leaves documents behind.** Client rules cannot delete a kitchen, so every document it writes
  stays; all their ids start with `smoke-` and the run prints the list. `--cleanup` deletes exactly
  that list with your Application Default Credentials, and refuses any path that is not a
  `smoke-` kitchen or invite. It also deletes the two anonymous users it created.
- **A failure on the first step** is the signature of a deployment older than the code: nothing
  works for a new user, which is exactly what it exists to catch.
- It never prints the project id or the key. Output is safe to paste into an issue.
- Against an emulator, set `FIRESTORE_EMULATOR_HOST` and `FIREBASE_AUTH_EMULATOR_HOST` (both) and
  use a `demo-` project id; no key is needed.

The callable functions are checked separately: `firebase functions:list --project <projectId>`
must show `suggestRecipes` and `writeCatalogue`, and `tools/smoke-agent.mjs` exercises
`suggestRecipes` end to end (`functions/README.md`).

## Indexes

`firestore.indexes.json` holds two arrays, `indexes` and `fieldOverrides`. Each repository
that needs a composite index appends one object to `indexes`, stating the collection group,
the query scope and the fields in query order:

```json
{
  "collectionGroup": "<subcollection>",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "<first>", "order": "ASCENDING" },
    { "fieldPath": "<second>", "order": "DESCENDING" }
  ]
}
```

Nothing else in the file changes, so two branches adding an index conflict only as two
sibling objects.

## Local configuration

`google-services.json` and `GoogleService-Info.plist` are never committed; CI restores them
from base64 secrets. Nothing in this directory contains a project id, key or bucket name.

## Seeding the catalogues

`taxonomies/`, their `terms/` and `ingredients/` are read-only for every client — the rules deny
those writes — so the documents get there through the Admin SDK, which bypasses rules. That is
the point and also the risk, which is why this is a deliberate command and not a CI step.

The vocabulary itself lives in `seed/*.json` and nowhere else. It must never move into a Kotlin
file: §1 of `docs/mvp-backlog.md` forbids a diet, an allergen, a cuisine, a unit or a storage
location appearing in code, and a dataset the app can read at build time is exactly that.

```bash
gcloud auth application-default login
cd tools && npm install
node seed.mjs --project <projectId>
```

It writes by document id, so running it twice leaves the same database — edit the JSON and run
it again to change a label. It prints counts and never contents.

Two taxonomies carry a `purpose` the app reads: `units` and `storage`. Everything else has none,
which is what tells the preferences screen to show a vocabulary without pretending to know what
it means. See #94.

Unit terms may also carry `conversion` (`dimension` and `factor`, see `docs/data-model.md`); the
seed script refuses to write a unit with an unknown dimension or a non-positive factor.
A countable unit may carry `pluralLabels` (same language keys as `labels`), used for any amount
but exactly one; the seed script refuses a plural in a language the term has no label for.
The seed has to be re-applied for a change to `pluralLabels` to reach the app.
