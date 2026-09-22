# KitchenAI data model — Firestore layout and document conventions

Normative for every data issue. The layout is implemented in
`shared/.../data/remote/firebase/FirestorePaths.kt`; no other file builds a path from
strings, so a change here is a change in exactly one place.

---

## Collection layout

```
users/{uid}                                     profile document
kitchens/{kitchenId}                            ownerId, memberIds, joinCode, memberDisplayNames
kitchens/{kitchenId}/pantry/{itemId}
kitchens/{kitchenId}/shoppingLists/{listId}
kitchens/{kitchenId}/shoppingLists/{listId}/items/{itemId}
kitchens/{kitchenId}/savedRecipes/{recipeId}
kitchenInvites/{joinCode}                       { kitchenId } only — resolves a code to a
                                                 kitchen without reading the kitchen document
taxonomies/{taxonomyId}                         read-only catalogue
taxonomies/{taxonomyId}/terms/{termId}
ingredients/{ingredientId}                      read-only catalogue
recipes/{recipeId}                              read-only catalogue
```

The profile stays user-owned, under `users/{uid}`; everything a kitchen's members share —
pantry, shopping lists, saved recipes — hangs off `kitchens/{kitchenId}` instead. Before
sharing existed (#190), all of it lived under `users/{uid}` and the rules were a two-line
owner check; a shared collection cannot be authorised that way, since the check is no longer
"does this uid match the path" but "is this uid a member of the kitchen the path names" —
`firebase/firestore.rules`' `isKitchenMember` does that lookup against
`kitchens/{kitchenId}.data.memberIds`. `kitchenInvites/{joinCode}` exists as its own top-level
collection, not nested under the kitchen it points at, precisely so a caller who is not yet a
member can resolve a join code (`kitchenInvites` grants `get` to any signed-in user) without
that read requiring — or granting — access to the kitchen document itself. A new
kitchen-shared collection goes under `kitchens/{kitchenId}` or it needs its own rule and its
own review; a new per-user collection still goes under `users/{uid}`.

The `users` collection itself is never listed: the rules deny it, and `FirestorePaths` has
no accessor for it. The `kitchens` collection can be listed by a signed-in caller, but only
for kitchens where `request.auth.uid` is already in `memberIds` — a single kitchen document
can still be fetched by id by anyone signed in, which is what lets a join transaction read
the kitchen an invite names before its own write makes the caller a member of it.

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
