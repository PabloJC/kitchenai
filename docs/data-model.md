# KitchenAI data model — Firestore layout and document conventions

Normative for every data issue. The layout is implemented in
`shared/.../data/remote/firebase/FirestorePaths.kt`; no other file builds a path from
strings, so a change here is a change in exactly one place.

---

## Collection layout

```
users/{uid}                                     profile document
users/{uid}/pantry/{itemId}
users/{uid}/shoppingLists/{listId}
users/{uid}/shoppingLists/{listId}/items/{itemId}
users/{uid}/savedRecipes/{recipeId}
taxonomies/{taxonomyId}                         read-only catalogue
taxonomies/{taxonomyId}/terms/{termId}
ingredients/{ingredientId}                      read-only catalogue
recipes/{recipeId}                              read-only catalogue
```

Everything user-owned hangs off `users/{uid}`. That is what lets the rules in
`firebase/firestore.rules` stay a two-line owner check instead of a per-collection audit. A
new user-owned collection goes under `users/{uid}` or it needs its own rule and its own
review.

The `users` collection itself is never listed: the rules deny it, and `FirestorePaths` has
no accessor for it.

---

## Document conventions

**Timestamps.** Stored as epoch milliseconds in a `Long`, never as a Firestore `Timestamp`.
GitLive's `Timestamp` does not round-trip through `kotlinx-serialization` the same way on
Android and iOS, and a field that decodes differently per platform is a bug that only one
half of the team can reproduce. Server-side ordering that needs true server time is a
post-MVP problem.

**Labels.** Human-readable text is a `Map<String, String>` keyed by language tag
(`{"en": "Chickpeas", "es": "Garbanzos"}`), never a bare `String`. Catalogue documents are
shared across users and languages.

**No contextual constants.** Diets, allergens, cuisines, units and storage locations are
documents under `taxonomies/`, referenced as opaque `TermRef`. No enum, no fallback list, not
even in test fixtures.

**Encoding.** DTOs are written with `encodeDefaults = true`. With defaults dropped, a field
holding its default value is absent from the payload, and on a merge write an absent field
means "leave it alone" — the two together make a reset to the default value silently
impossible.

**Identifiers.** Generated on the client through `IdGenerator` (`UuidIdGenerator`). The
shopping list has to create documents while offline, so an id can never come from the server.

---

## Errors and offline

Every suspending call goes through `firestoreCall`, every snapshot flow through
`asAppResultFlow`; both map failures with `Throwable.toAppError()`. `CancellationException`
is rethrown, never mapped.

Local persistence is enabled where `FirebaseFirestore` is built, in
`shared/.../di/FirebaseModule.kt`. It is the default on both platforms and is stated
explicitly because the shopping list depends on reading its own writes while offline.
