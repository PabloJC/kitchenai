# The agent contract

What the app sends to the `suggestRecipes` callable function and what it accepts back.

**The function is not in this repository.** Authoring and deploying it is backend work; this
document is the half of the contract the client implements, and the client fails loudly rather
than adapting when a response does not match it.

---

## Why a callable function at all

A model provider key shipped inside a mobile binary is a published key. So the client holds no
credential, names no provider and builds no prose. The function holds the key, chooses the
model, owns the instruction template, and resolves identifiers to words against the same
taxonomy documents the app reads.

That split is the reason for the shape below: **every value the client sends is an identifier
or a number.** There is no field in which a sentence can leave the device.

---

## Request

```json
{
  "schemaVersion": 1,
  "requestId": "<uuid>",
  "capability": "SUGGEST_FROM_PANTRY",
  "languageTags": ["en", "es"],
  "servings": 2,
  "options": { "maxResults": 5, "maxMinutes": null, "useOnlyPantry": true,
               "useExpiringSoon": false, "mealType": null },
  "constraints": [ { "taxonomy": "<id>", "term": "<id>", "strength": "AVOID" } ],
  "preferences": [ { "taxonomy": "<id>", "term": "<id>" } ],
  "avoidedIngredients": ["<ingredientId>"],
  "pantry": [
    { "ingredientId": "<id>", "amount": 1.0, "unitTaxonomy": "<id>",
      "unitTerm": "<id>", "expiringSoon": false }
  ]
}
```

`requestId` is the client's, so a retry is recognisable server-side as the same question.

`useExpiringSoon` and `mealType` (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` or `null`) were added
in #293 and are optional: a client that omits them asks for no preference.

`strength` is one of `PREFER`, `AVOID` — how hard a constraint binds is app logic, not
vocabulary, which is why it is an enum while the term it applies to is not. `AVOID` is the one
the function must treat as absolute: `PREFER` is taste, `AVOID` can be an allergy. A request may
still carry a legacy `EXCLUDE` from a client that has not updated (#180) — treat it exactly as
`AVOID`, never softer.

`expiringSoon` is the reason the whole feature exists; the function is expected to weight it.

---

## Response

```json
{
  "schemaVersion": 1,
  "agentId": "<id>",
  "modelId": "<id>",
  "suggestions": [
    {
      "title": "...",
      "summary": "...",
      "servings": 2,
      "totalMinutes": 30,
      "ingredients": [
        { "ingredientId": "<id>", "freeText": null, "amount": 200.0,
          "unitTaxonomy": "<id>", "unitTerm": "<id>", "optional": false }
      ],
      "steps": ["..."],
      "tags": [ { "taxonomy": "<id>", "term": "<id>" } ],
      "imageKey": "<64 hex characters>"
    }
  ]
}
```

No suggestion carries an id. A dish that has never been saved has no identity the server could
know, so the client mints one.

`modelId` is what stamps the suggestion's provenance. It is taken from the response, never from
what the client expected — a suggestion has to be able to say which model wrote it.

Every suggestion's `tags` should include exactly one term from the `dish-types` taxonomy — the
dish's shape (`soup`, `pizza`, `salad`...), not its diet or cuisine — alongside whatever
`diets`/`cuisines` tags apply. It is the same array, same `{taxonomy, term}` shape as any other
tag; a suggestion missing it is not invalid, just one #196's photo fallback has to handle.

`imageKey` (#370) is optional and may be `null`. It is the only thing the client sends back to
ask for that dish's photograph, see [Recipe images](#recipe-images).

---

## Recipe images

The `recipeImage` callable returns a photograph of one suggested dish.

```json
{ "schemaVersion": 1, "imageKey": "<the suggestion's imageKey>" }
```

```json
{ "schemaVersion": 1, "imageUrl": "https://..." }
```

The key is a hash `suggestRecipes` issued, and the title and summary behind it stay on the
server, so this call keeps the rule above: no sentence leaves the device. The image is generated
once per key and stored; later calls return the stored URL and cost nothing.

| Code | Meaning |
|---|---|
| `INVALID_ARGUMENT` | not a key this function could have issued |
| `FAILED_PRECONDITION` | a well-formed key with nothing behind it (its source expired before any image was made) |
| `RESOURCE_EXHAUSTED` | the account's daily image allowance is spent |
| `UNAVAILABLE` | the model failed or withheld the image |

Every one of them means the same to the client: keep the dish-type photo.

---

## What the client does with a response

`SuggestionValidator` is the only way a payload becomes a `Recipe`, and it treats the response
as untrusted input, because it is: it reaches a screen and a Firestore document.

| Rule | Consequence |
|---|---|
| `schemaVersion` must equal 1 | the whole response fails |
| `agentId` or `modelId` blank | the whole response fails |
| more suggestions than `maxResults` | the extras are dropped, after validation |
| more than 50 suggestions | never even inspected |
| no title, no steps, no lines, or `servings < 1` | that suggestion is dropped, its siblings survive |
| an ingredient that is neither a pointer nor free text | that line is dropped |
| an `ingredientId` that will not parse | that line is dropped, **not** downgraded to free text |
| `amount <= 0` or `totalMinutes <= 0` | the field becomes absent rather than nonsense |
| a tag missing either half | that tag is dropped |
| control characters in any text | stripped |
| title over 120, summary over 400, a step over 1000 | truncated |

Two rules run through all of it:

- **A bad suggestion costs itself, never its siblings.** One unusable dish must not empty a list
  the user is waiting on.
- **No field of the response is ever read as an instruction.** Everything the validator does is
  length, shape and character class. If a later issue makes the client *act* on a field, that
  field goes through the validator first.

---

## Schema version policy

`schemaVersion` is a single integer on both sides, and the client speaks exactly one.

- **Adding a field** does not change it. Unknown keys are ignored, so a server-side addition
  does not break a client already in the field. A **missing required** key still fails.
- **Removing or repurposing a field** does change it — and an older client will then refuse
  every response, which is the intended behaviour. It surfaces as a failure the user can retry
  past rather than as silently wrong recipes.
- Because the client refuses rather than degrades, the function must keep answering the old
  version for as long as clients speaking it are in the wild.

---

## Errors

The call fails through the same mapping every Firebase call in this app uses:

| Code | `AppError` |
|---|---|
| `UNAUTHENTICATED`, `PERMISSION_DENIED` | `Unauthorized` |
| `UNAVAILABLE`, `DEADLINE_EXCEEDED` | `Network` |
| `RESOURCE_EXHAUSTED` | `Unknown` |
| `NOT_FOUND` | `NotFound("function")` |
| anything else | `Unknown` |

`NOT_FOUND` from a callable means the function is not deployed, which is a different problem
from a missing document; it is named separately so the two never read alike in a log or on a
screen.

`RESOURCE_EXHAUSTED` is retryable, and #51 asked for `Network` on that ground. It is mapped to
`Unknown` instead: every screen renders `Network` as "No connection", and a rate-limited user
has a connection. Retryability is not what that error type means to the person reading it.

The call carries an explicit **30 second timeout**. A model call may be slow, but a stalled one
has to fail rather than spin: without it the caller waits on the platform default, which is a
minute on Android and effectively unbounded on a dead connection.

Nothing throws. Every failure leaves the agent as an `AppResult.Failure`, except cancellation,
which propagates as it must.
