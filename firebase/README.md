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
