# Session handling

How `SessionViewModel` keeps the app on the right Firebase uid, and why it fails the way it does.
Code comments stay short and point here.

## Who owns what

`SessionViewModel` is the single writer of a missing `users/{uid}`. `SessionGate` composes the app
only while the state is `Ready(uid)`; every screen below it reads data keyed to that uid.

The uid can change after `Ready`: Google sign-in swaps the Firebase user outright rather than
linking (#186), and signing out clears it. The ViewModel follows `observeSession()` and sets up
the new uid (kitchen, default shopping list, profile listener) before `Ready` moves to it.

## Failure states

| State | When | Retry |
|---|---|---|
| `Failed` | Cold start: no session, or setup of the first uid failed | Re-runs the whole bootstrap (idempotent) |
| `SwitchFailed` | A uid change after `Ready` failed, or the profile write of such a uid | Re-follows the session Firebase reports now |

`SwitchFailed` exists because the user is already signed in. The failure is about the setup of
their new uid, so the retry must never repeat the sign-in, and the cold-start screen would suggest
the app has no session at all. The previous uid is not kept on screen: Firebase has already
dropped it, so every read under it would fail.

Which of the two a failed profile write ends in depends on how the active uid was established
(`switched`, set when the uid changes), not on who called `createProfile`.

## Invariants

- One setup at a time (`setup` mutex): bootstrap, the session watcher and a switch retry would
  otherwise race `ensureKitchen`'s read-then-create.
- A retry for the uid already active keeps its profile listener and its flags; a different uid
  cancels the listener and resets `profileMissing` / `creatingProfile`.
- An event from a listener of a dropped uid is ignored (`onProfileError`, `failProfileWrite`).

## Pending display name

A Google sign-in returns a display name that must end up on the profile of the new uid, and the
screen that received it can be torn down before that happens (a `SwitchFailed` removes everything
under the gate). So the name does not live in `ProfileViewModel`:

1. `ProfileViewModel` hands it to `PendingDisplayName` (a Koin single) before the sign-in starts,
   unbound to any uid, and drops it if the sign-in fails.
2. Whoever learns the new uid first binds it: `SessionViewModel` when it follows a non-anonymous
   session, or `ProfileViewModel` when the sign-in returns. A bound entry never moves to another
   uid and is dropped when a different uid becomes active.
3. `SessionViewModel` applies a bound entry once the profile of that uid exists: written together
   with the profile when it creates it, or saved over the loaded one otherwise. An unbound
   entry is never applied, so the profile of the previous uid cannot receive the new name.

A failed name write is not a failure state: the name stays pending and is tried again when the
profile next emits. The write is a read-modify-write of the loaded profile, so an edit saved from
the profile screen in the same instant can be overwritten; the window is the round trip of that
one write.
