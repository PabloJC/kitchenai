# `suggestRecipes`

The server half of the recipe agent. `docs/agent-contract.md` is the wire contract; the Kotlin
DTOs under `shared/data/remote/agent/dto` are the other implementation of it, and the two change
together or the feature breaks in the field.

## What it is for

A model provider key inside a mobile binary is a published key. So the app holds no credential
and builds no prose: it sends identifiers and numbers, and everything that turns those into
language happens here.

Concretely:

1. **Parse and clamp.** `contract.ts` refuses anything that is not a short identifier. That
   alphabet is the design, not a nicety — it is the boundary the client's text cannot cross, so
   a compromised app cannot use this endpoint to talk to a model in its own words.
2. **Resolve.** `catalogue.ts` turns `units/gram` into "g" against the same Firestore documents
   the app reads, in the user's own language.
3. **Ask.** `suggest.ts` owns the instruction template and calls the model with a response
   schema, so a malformed answer is a failed call rather than a bad suggestion.
4. **Point back at the catalogue.** A model works in words; the pantry cannot be compared with
   a word. An ingredient the model claims is in the catalogue is kept as an id only if the
   catalogue agrees, and dropped otherwise.

## Model

Gemini through Vertex AI **in this same project**, so there is no key anywhere: the function
authenticates with its own service account.

## Regions

| Setting | Default | Why |
|---|---|---|
| `FUNCTIONS_REGION` | `europe-southwest1` | Where this project's Firestore lives. The function reads the whole catalogue on every call, so co-locating them is the single biggest thing here. |
| `VERTEX_LOCATION` | `europe-west1` | Keeps inference in the EU. A pantry is a list of what someone eats and when it spoils, and it is the only thing this call sends anywhere. |

Both are overridable. If a deploy rejects `europe-southwest1` for Cloud Functions v2, fall back
to `europe-west1` and accept the extra hop to Firestore. If a model is not served from
`europe-west1`, `global` works but routes outside the EU — a choice worth making deliberately.

## Rate limit

`enforceAppCheck` answers "is this our app?". It does not answer "is our app asking ten
thousand times?", and on Blaze that second question costs money.

`quota.ts` counts calls per account per UTC day and refuses past `DAILY_CALL_LIMIT` (50 by
default, overridable). The counter lives in `agentUsage`, which the rules deny to every client —
the function writes it through the Admin SDK, which bypasses them. A quota a caller can edit is
not a quota.

Each document carries `expiresAt`, and a Firestore **TTL policy** on that field is active, so
the collection clears itself without a scheduled job. Deletion runs within 24 hours of expiry
and is billed as a write — negligible at one document per account per day.

To recreate it on another project:

```bash
curl -X PATCH -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" \
  -H "x-goog-user-project: <project>" -H "Content-Type: application/json" -d '{"ttlConfig":{}}' \
  "https://firestore.googleapis.com/v1/projects/<project>/databases/(default)/collectionGroups/agentUsage/fields/expiresAt?updateMask=ttlConfig"
```

## App Check

`enforceAppCheck: true`. Without it, anyone holding the URL spends this project's model budget.

**A debug build will be refused** until its debug token is registered under
*App Check → Apps → Manage debug tokens* in the Firebase console. That is the intended
behaviour, not a misconfiguration.

## Prerequisites

Checked against this project on 2026-08-13:

- **Blaze plan — not yet.** `cloudbilling.googleapis.com` reports `billingEnabled: false`, so
  the project is on Spark and a deploy will be refused. Upgrading needs a card and is the
  owner's to do, in *Firebase console → Usage and billing → Modify plan*.
- **Cloud Functions API — not enabled.** Expected; it has never been used here. Enabling it is
  part of the first deploy, or can be done from the console.
- **Vertex AI API** must be enabled in the Google Cloud project.
- The catalogues must be seeded (`tools/seed.mjs`), or every request resolves to nothing and
  the model is asked to cook from an empty kitchen.

## Commands

```bash
npm --prefix functions install
```

```bash
npm --prefix functions test
```

```bash
firebase deploy --only functions --project <your-project-id>
```

## Tests

`node --test` over the pure parts: request parsing, label resolution, and the mapping back to
identifiers. The model call itself is not tested here — it is one `generateContent` call, and a
test of it would assert that a mock returns what the mock was told to return.

What is worth reading is `contract.test.ts`: it covers the injection attempt, the oversized
pantry, and the model inventing a catalogue id.
