# Manual verification checklist

What no unit test reaches: the platform Google sheet, a real uid swap, two accounts sharing one
kitchen, and bundled images rendering on both platforms. Every PR in the sharing and Google
sign-in work (#187–#194, #196, #207, #209) lists a pass like this as *not done* because the agents
that wrote them had no Firebase-configured device. This file writes it down once, so the pass is
reproducible and its result can be recorded at the bottom.

**Status: written from the code, never run.** Every screen, button and message below was read from
`composeApp/src/commonMain/composeResources/values/strings.xml` and the presentation code, not
observed on a device. A line that does not match what you see is a finding — record what you saw
verbatim in the notes column, do not bend it to fit.

Conventions used below:

- Quoted text is the English string from `values/strings.xml`. Run the device in English; the
  Spanish file (`values-es/strings.xml`) carries the same keys with different wording.
- `<uid>`, `<kitchenId>`, `<code>` and `<projectId>` are placeholders. Never paste a real one into
  an issue, a PR or this file.
- "Console" means the Firebase console (or the Emulator UI where noted) for the project the build
  points at.

---

## Prerequisites

### Firebase config files

Neither file is ever committed (both are in `.gitignore`):

| Platform | File | Where it goes |
|---|---|---|
| Android | `google-services.json` | `androidApp/google-services.json` |
| iOS | `GoogleService-Info.plist` | `iosApp/iosApp/GoogleService-Info.plist`, next to `Info.plist` — one level up the app builds and then dies on launch (see `iosApp/README.md`) |

Download both from the Firebase console (*Project settings → Your apps*). CI restores them from the
`GOOGLE_SERVICES_JSON` and `GOOGLE_SERVICE_INFO_PLIST` secrets (base64), which is why a local
checkout has to supply its own.

### Build properties

| Setting | Android | iOS |
|---|---|---|
| Functions region | `kitchenai.functionsRegion` Gradle property (default in `gradle.properties`; the build fails if it is blank). Reaches the app as `BuildConfig.FUNCTIONS_REGION` | `FUNCTIONS_REGION` in `iosApp/Configuration/Config.xcconfig`, read through `Info.plist` |
| Google sign-in client id | `kitchenai.googleWebClientId` Gradle property — the OAuth **Web** client id. Blank by default; reaches the app as `BuildConfig.GOOGLE_WEB_CLIENT_ID` | `GOOGLE_IOS_CLIENT_ID` in `Config.xcconfig`, read by `GIDClientID` in `Info.plist`, plus `GOOGLE_IOS_REVERSED_CLIENT_ID` (the same id with its dot-separated parts reversed), registered as the app's URL scheme. Both blank by default |

The region must match `FUNCTIONS_REGION` in `functions/`; client and deployment change together or
the suggestion call reaches nothing. Where the Google client ids come from: *Firebase console →
Authentication → Sign-in method → Google* (Web SDK configuration), and *Project settings → your
iOS app* once Google is enabled as a provider.

Supply the Android value without committing it, either on the command line or in your own
`~/.gradle/gradle.properties`:

```bash
./gradlew :androidApp:installDebug -Pkitchenai.googleWebClientId=<webClientId>
```

For iOS, fill `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_IOS_REVERSED_CLIENT_ID` locally in `Config.xcconfig`
(the Cloud console shows the second as "iOS URL scheme") and **do not commit the change**. With a client
id and no matching scheme the app stops at launch with a message naming the setting; with both blank it
runs and logs that Google sign-in is not configured; signing in cannot work.
`TEAM_ID` in the same file stays empty on purpose (`docs/infra.md`); run on a simulator or sign with
a personal team.

### Firebase project

- *Authentication → Sign-in method*: **Anonymous**, **Google** and **Apple** enabled. Apple needs no
  Services ID for the native iOS flow: the bundle id is enough (#251).
- Sign in with Apple also needs the *Sign in with Apple* capability on the App ID in Apple Developer
  (`iosApp/iosApp/iosApp.entitlements` asks for it). A personal team cannot sign that capability, so
  scenario 12 needs a paid team in `TEAM_ID`; the simulator still runs without it, minus Apple sign-in.
- The Android app registered in the project with the debug keystore's SHA-1 fingerprint added —
  the Google sheet fails without it. This is general Firebase behaviour, not something this
  repository checks.
- The catalogues seeded (`node tools/seed.mjs --project <projectId>`, see `firebase/README.md`),
  otherwise the dish-type and constraint labels have nothing to resolve against.
- The rules deployed from `firebase/firestore.rules`.
- A debug App Check token registered for each device or simulator, per platform
  (`docs/infra.md`, *App Check*). Without it the failure arrives as a permission error that looks
  like a rules problem.
- For the photo scenarios only: the `suggestRecipes` function deployed and callable.

### Building and running

```bash
# Android
./gradlew :androidApp:installDebug

# iOS: build the framework once, then run iosApp/iosApp.xcodeproj on a simulator from Xcode
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

### The Firestore emulator does not apply to the app

Neither platform has a code path that points the SDKs at an emulator, so none of the scenarios
below can run against `firebase emulators:start`. What the emulator does cover is the sharing
rules the kitchen scenarios exercise from the client:

```bash
cd firebase/tests && npm install && npm test
```

Its cases for join, leave, owner removal, "removed member cannot rejoin" and join-code
regeneration are the machine-checked half of scenarios 5 to 8; the device pass checks that the app
produces exactly the writes those rules accept, and shows the right message when they refuse.

### What you need

- One Android device or emulator and one iOS simulator (or two of one platform; running one of
  each also checks that the two platforms agree on the data).
- Two Google accounts you control, for scenarios 2, 3 and 5 onward. Use throwaway accounts, never
  your own.
- Access to the Console, to read documents and to edit one in scenario 10.

### Starting from a fresh install

An anonymous session survives restarts, so "first launch" needs a clean slate:

- Android: uninstall the app, or `adb shell pm clear com.kitchenai.app`.
- iOS simulator: erase the simulator (`xcrun simctl erase <device>`) or its content and settings.
  Deleting the app alone may leave the previous anonymous session in the keychain — if scenario 1
  shows an old kitchen, that is why, not a bug.

---

## Scenarios

Each one lists the screen, the action and the visible result. "Console" lines are the checks only
the backend can answer. Run every scenario on both platforms unless it says otherwise.

### 1. First launch: anonymous session, kitchen and default shopping list

*Start from a fresh install.*

1. Launch the app.
   - A loading indicator shows while the session resolves. No error text appears at any point.
2. Wait for the shell.
   - The **Pantry** tab is selected, with its top bar titled "Pantry" and the empty state "The
     pantry is empty" / "Add what you already have and the suggestions start working".
   - The bottom bar shows four tabs: "Pantry", "Shopping", "Ideas", "Profile".
3. Tap **Shopping**.
   - The empty state "Nothing to buy" / "Add what you need and tick it off as you go."
4. Tap **Profile**.
   - A button "Sign in with Google" at the top, the line about what every suggestion sends, and a
     "Manage your kitchen" button.
5. Tap "Manage your kitchen".
   - Top bar "Kitchen" with a back arrow. Under "Your join code", a code with a "Copy" button and,
     because you own the kitchen, a "New code" button.
   - Under "Members", one row ending in "(you)" with the supporting text "Owner". An anonymous
     account has no display name, so the row shows the member id instead of a name.
   - "Leave this kitchen" is enabled and no explanatory message is shown above it.
   - Under "Join a kitchen", an empty field with the placeholder "Enter a join code" and a disabled
     "Join" button.
6. Console.
   - *Authentication → Users*: one new user with no provider (anonymous).
   - `users/<uid>`: exists, with `languageTags` set to the device language and `displayName`
     absent.
   - `kitchens/<kitchenId>`: `ownerId` is `<uid>`, `memberIds` is `[<uid>]`, `joinCode` equals the
     code on screen.
   - `kitchenInvites/<code>`: exists, holding only `kitchenId`.
   - `kitchens/<kitchenId>/shoppingLists/<listId>`: one document whose `labels` maps the device
     language tag to "Shopping list".
7. Kill the app and launch it again.
   - Same uid, same kitchen, still exactly one shopping list: nothing is created twice.

### 2. Google sign-in: linking keeps the guest's data, an existing account asks before switching

*Continue from scenario 1, still anonymous. Add one item first so there is something to keep:
Shopping tab, field "Add an item", type a word, tap "Add". Use a Google account that has never
signed in to this Firebase project (delete it from Authentication → Users first if it has).*

1. Cancel path first. Profile tab, tap "Sign in with Google" and dismiss the sheet without choosing
   an account.
   - The button is enabled again, a red line "Something went wrong" appears above the "Save"
     button, and the session is unchanged: still anonymous, same kitchen.
2. Under the button, a grey line reads "Keeps your pantry, list and recipes, and lets you use them
   on other devices." Tap "Sign in with Google" again.
   - The platform's own Google sheet opens (Credential Manager on Android, the Google Sign-In view
     on iOS). While it is up, the button is disabled and a second tap does not stack a second sheet.
   - iOS only: the sheet needs the reversed client id registered as a URL scheme and the callback
     forwarded to `GIDSignIn.handle(url)` (Google's and KMPAuth's setup both require it; the app does
     both). After you pick an account the app comes back to the foreground. If the sheet opens but
     control never returns, check `GOOGLE_IOS_REVERSED_CLIENT_ID` first, then record it.
3. Pick the never-used throwaway account.
   - No "Switch to this account?" dialog appears.
   - The "Sign in with Google" button is replaced by a row with the account's Google name on the
     left and a "Sign out" button on the right. If Google returned no name the row reads "Signed
     in".
   - No email or photo appears anywhere.
   - The screen never shows the "This account is not allowed to read its own data" message, not
     even for a moment.
4. Check the other tabs.
   - **Pantry** and **Shopping** both load, without an error state, and still hold what you added
     as a guest: the anonymous user was linked to Google, not swapped for it (#250).
   - **Ideas** loads without an error.
5. Profile tab, "Manage your kitchen".
   - The same join code as in scenario 1, and one member ending in "(you)" and "Owner".
6. Console.
   - *Authentication → Users*: the uid from scenario 1, now with the Google provider. No new user.
   - `users/<uid>`: `displayName` equals the Google name.
   - Still exactly one `kitchens` document and one `shoppingLists` document for this uid.
7. An account that already has data. Sign out, add one item as the new guest, then tap "Sign in
   with Google" and pick the **same** Google account again.
   - A dialog "Switch to this account?" explains that the guest data stays behind, with
     "Stay as guest" (filled) and "Switch".
   - "Stay as guest": the dialog closes, no error appears, the row still offers "Sign in with
     Google", and the guest item is still on Shopping.
   - Repeat and tap "Switch": the row shows the Google name and Pantry/Shopping show the data from
     steps 1–6, not the guest item.
8. Profile tab: tap a term chip in one of the constraint sections, then tap "Save".
   - The button reads "Saving" and then returns to a disabled "Save". No error text appears.

### 3. Sign-out: a fresh anonymous session, no permission errors

*Continue from scenario 2, signed in with Google.*

1. Profile tab, tap "Sign out".
   - The row goes back to the "Sign in with Google" button.
   - The screen briefly reloads but never shows "This account is not allowed to read its own data"
     or any other error.
2. Check the other tabs.
   - **Pantry** and **Shopping** load empty and **Ideas** loads, none of them with an error state.
     This is a new anonymous account, not the Google one with its data hidden.
3. "Manage your kitchen".
   - A third join code, one member, labelled "You" (an anonymous account has no name).
4. Console.
   - *Authentication → Users*: a new anonymous user, a uid you have not seen yet.
   - A new `kitchens` document and a new `shoppingLists` document for it.
5. Sign in again with the same Google account and tap "Switch" in the dialog.
   - The data from scenario 2 is back: the same kitchen and code as in scenario 2, and the
     shopping item you added there.

### 4. A failed account switch recovers through "Try again"

*This is the failure `SessionViewModel` keeps recoverable after a mid-session uid change (#209).
Easiest to force on the sign-out half, which only needs the network for the step after it.*

1. Signed in with Google, put the device in airplane mode.
2. Profile tab, tap "Sign out".
   - Signing out is local, so it succeeds. Creating the replacement anonymous session needs the
     network, so it fails.
   - The whole app is replaced by a centred red message and a "Try again" button. The expected
     message is "No connection"; if another appears, record it.
3. Tap "Try again" **while still offline**.
   - The same failure screen returns. The app does not crash and the tabs do not appear.
4. Turn the network back on and tap "Try again".
   - A loading indicator, then the shell on the **Pantry** tab. Profile shows "Sign in with
     Google", not the signed-in row.
5. Console.
   - *Authentication → Users*: exactly **one** new anonymous user from this attempt, not one per
     tap of "Try again". Retrying re-follows the session Firebase reports and never repeats the
     sign-out.
   - One new `kitchens` document and one `shoppingLists` document for it.

### 5. Two accounts on two devices: join code, member list, shared data

*Device A and device B, each on a fresh install of its own platform. Sign both in with Google (two
different accounts) if you want names in the member list; anonymous works for everything else.*

1. Device A: Profile, "Manage your kitchen". Read the code under "Your join code". Tap "Copy" and
   paste it somewhere you can read it on B, or type it by hand; the case does not matter, so try
   typing it in upper case on B.
2. Device B: Profile, "Manage your kitchen". Type A's code into the field "Enter a join code".
   - "Join" becomes enabled as soon as the field is not blank.
3. Tap "Join". B already has a solo kitchen, so a dialog "Join this kitchen?" says the current one
   will no longer be visible (#253). "Cancel" changes nothing; tap "Join" in the dialog.
   - The field empties and a snackbar reads "You joined the kitchen".
   - B's screen now shows A's code under "Your join code" and two members: A first, with the
     supporting text "Owner", then B ending in "(you)".
   - B has **no** "New code" button and **no** "Remove" button. "Leave this kitchen" is enabled.
4. Device A, without leaving the screen.
   - The same two members appear live, no relaunch. A sees a "Remove" button on B's row and none on
     its own. "New code" is visible.
5. Member names.
   - Joining passes the profile display name. B's row shows B's Google name if B signed in with
     Google, and the label "Kitchen member" on A's screen (and "You" on B's) if B is anonymous. A
     raw id never shows. Record what both devices show.
6. Shared data. On A, Shopping tab, field "Add an item", type a word, tap "Add".
   - The item appears under "To buy" on B within a few seconds, with no relaunch. Tick it on B
     ("In the cart" section appears) and watch A follow.
7. Console.
   - `kitchens/<kitchenId>.memberIds` holds both uids and `ownerId` is still A's.
   - B's earlier solo kitchen still exists with an empty `memberIds` (joining leaves the previous
     kitchen in the same write).
8. Last, on device B: in "Join a kitchen", type a made-up string and tap "Join".
   - The red line "That code does not match any kitchen".
   - B is still in A's kitchen: the Kitchen screen still lists both members, and the Pantry and
     Shopping tabs still show the shared items. Leaving and joining are one transaction, so a bad
     code changes nothing. Record anything else.

### 6. Leaving: what the owner and a member may do

*Continue from scenario 5: A owns a kitchen with B in it.*

1. Device A (owner, two members): look at the bottom of the Kitchen screen.
   - A muted line "As the owner, you cannot leave while others are still in this kitchen. Remove
     them first." sits above "Leave this kitchen", and the button is **disabled**.
2. Device A: in "Join a kitchen", enter any code, tap "Join" and confirm in the dialog.
   - The same sentence appears as the error, since joining another kitchen would first leave this
     one. A is still in its kitchen afterwards.
3. Device B (member): tap "Leave this kitchen".
   - A dialog "Leave this kitchen?" opens, its "Leave" button in red. "Cancel" first: nothing
     changes. Tap it again and confirm with "Leave"; a snackbar reads "You left the kitchen".
   - B's screen may show the empty state "No kitchen yet" for a moment, then settles on a new solo
     kitchen: a different join code, B as the only member and "Owner", with no restart. The session
     provisions the replacement.
   - B's **Pantry** and **Shopping** tabs end up empty (the shared items are not carried over) and
     any error banner they showed in between goes away. Record how long that took.
4. Device A.
   - B's row disappears, live. The muted line and the disabled state of "Leave this kitchen" go
     away, since A is alone again.
5. Console: `memberIds` no longer holds B and `memberDisplayNames` has no entry for B.
   `removedMemberIds` is **not** extended by a voluntary leave.
6. Device A (now a solo owner): tap "Leave this kitchen" and confirm.
   - It works. A's screen may show "No kitchen yet" for a moment, then a new solo kitchen with a
     different code.
   - Console: the old kitchen document remains, with an empty `memberIds`; no member can reach it
     again. A new `kitchens` document lists A as the only member.

### 7. Owner removes a member; a removed member cannot rejoin

*Rebuild the sharing from scenario 5 with fresh accounts if needed: A owns, B has joined.*

1. Device A: Members, B's row, tap "Remove".
   - A dialog names B ("Remove <name> from the kitchen?", or "Kitchen member" if B has no name) and
     says B cannot rejoin with the code; its "Remove" button is red. Confirm.
   - B's row disappears from A's list and a snackbar confirms it.
2. Device B.
   - Live, the Kitchen screen changes to "No kitchen yet" and then to a new solo kitchen of its own,
     without a restart.
3. Console.
   - `memberIds` no longer holds B; `removedMemberIds` now holds B's uid, and only B's.
4. Device B: in "Join a kitchen", type A's **current** code (still valid), tap "Join" and confirm.
   - The join is refused with a red line. The expected message is "This account is not allowed to
     do that"; if another appears, record it verbatim.
   - B does not appear in A's member list, and the Console `memberIds` is unchanged.

### 8. Owner regenerates the join code

*A owns a kitchen. Use a fresh install C that is not a member (not B: scenario 7 blocked it from
this kitchen).*

1. Device A: note the code, then tap "New code" next to "Your join code".
   - A dialog "Make a new code?" says the current code stops working. Confirm with "New code".
   - The displayed code changes immediately, with no error, and a snackbar reads "New code ready".
2. Console.
   - `kitchens/<kitchenId>.joinCode` equals the new code.
   - `kitchenInvites/<oldCode>` is **gone** and `kitchenInvites/<newCode>` exists.
3. Spare account: enter the **old** code and tap "Join".
   - "That code does not match any kitchen".
4. Spare account: enter the **new** code and tap "Join".
   - It joins; A's member list shows it live.

### 9. Recipe photos by dish type: list card and detail header

*Needs the `suggestRecipes` function working. A pantry with a handful of items gives the model
something to work from. Run on both platforms.*

1. Ideas tab. Pull down on the list ("Pull down for ideas built from what your pantry already
   holds").
   - A line "Reading your pantry and thinking of dishes. This takes a moment." and three skeleton
     cards, each with a grey placeholder block holding an image icon.
2. When results arrive, each card has a **photograph** in the top 16:9 slot, then the title, a
   "Generated" badge and a minutes badge.
   - The photograph depends on the recipe's `dish-types` tag. Compare it to the dish: a pasta dish
     shows a pasta photo, a soup a soup photo, and so on.
   - No card keeps a grey placeholder block unless its recipe has no usable `dish-types` tag
     (scenario 10).
3. Tap a card.
   - The top bar shows the recipe title, with a back arrow, and the photograph is at the top of
     the screen, edge to edge, the same one as on the card.
   - The tag row includes a chip with the dish type's label, for example "Soup".
4. Tap the back arrow. The list is where you left it, with the same photos.
5. Check at least three different dish types per platform. The 24 `dish-types` terms and their
   bundled photos are: `pasta`, `soup`, `salad`, `stew`, `curry`, `stir-fry`, `roast`, `grilled`,
   `sandwich`, `pizza`, `rice-dish`, `noodles`, `dessert`, `baked-goods`, `breakfast`, `seafood`,
   `tacos-wraps`, `casserole`, `bowl`, `bbq`, `eggs`, `dumplings`, `flatbread`, `smoothie`. A
   generated set will not cover them all; scenario 10's console edit lets you step through the rest
   on a saved recipe.
6. Record, per platform: whether every photo loads, whether any is stretched, cropped so the dish
   is cut off, or visibly slow to appear, and whether the card and the detail header show the same
   image.

### 10. A recipe with no `dish-types` tag falls back to the placeholder

*Deterministic, so it does not depend on what the model returns. Needs scenario 9's list.*

1. Open any suggestion's detail and tap the heart in the top bar (labelled "Save"; it turns filled
   and a message "Saved" appears).
2. Back on the Ideas tab, scroll to the bottom: a "Saved recipes" header, and the saved recipe as a
   card below it, with its photo.
3. Console: open `kitchens/<kitchenId>/savedRecipes/<recipeId>`. In `tags`, remove the entry whose
   `taxonomy` is `dish-types` and save the document.
   - Within a few seconds the card's photograph becomes a plain grey block holding an image icon,
     in the same 16:9 slot, with no shift in the layout. Nothing crashes. If it does not update
     until you reopen the screen, record that.
4. Tap the card.
   - The detail header shows the same grey block in place of the photograph.
5. Console: put a `dish-types` entry back, but with a term that is **not** one of the 24 listed in
   scenario 9 (for example `not-a-dish`).
   - The grey block stays: an unknown term is a miss like any other.
6. Console: set `term` to each of a few known values in turn (`pasta`, `soup`, `dessert`).
   - The photograph changes to match each.

### 11. A hand-typed shopping line carries an amount and moves to the pantry

*Run on both platforms. Check the keyboard in steps 1 and 5.*

1. Shopping tab. Tap "Add an item", type a word that is not in the catalogue, and look at the row
   under it.
   - An "Amount" field and a unit selector sit beside the "Add" button, and with the keyboard open
     the name field, the amount field and "Add" are all visible and tappable.
2. Type `2` in "Amount", choose "kg" in the unit selector and tap "Add".
   - The line appears in "To buy" with "2 kg" on its right. The amount field is empty again.
3. Add a second line with no amount.
   - It shows an "Add amount" button where the amount would be.
4. Tick both lines, then tap "Move to pantry".
   - The message is "1 moved to the pantry, 1 has no amount and stays on the list". The "2 kg"
     line is gone from the list and the Pantry tab holds it with "2 kg".
5. Tap "Add amount" on the remaining line.
   - A dialog with the amount field and the unit selector opens and stays usable with the keyboard
     open. "Save" stays disabled until the amount is a number above zero.
6. Enter `500`, choose "g", tap "Save", then tick the line and tap "Move to pantry".
   - The line shows "500 g" and moves; the Pantry tab holds it.
7. Add another line with no amount, tick only it, tap "Move to pantry".
   - The message says nothing moved because the line has no amount and points at "Add amount";
     the line stays on the list.

---

### 12. Sign in with Apple (iOS only)

*iOS, from a fresh install (anonymous). Add one shopping item first. Use an Apple ID that has never
signed in to this Firebase project; in Settings → Apple ID → Sign-In & Security → Sign in with Apple,
stop using Apple ID for the app to reset it.*

1. Profile tab.
   - Apple's own black "Sign in with Apple" button (white in dark mode) sits above "Sign in with
     Google", both full width and the same height.
   - On Android the Apple button does not appear at all.
2. Tap it and cancel Apple's sheet.
   - A red "Something went wrong" line, the buttons enabled again, still a guest.
3. Tap it again, share the name, and continue (hiding the email is fine).
   - The account row shows the name Apple returned; the shopping item is still there (linked, same uid).
   - *Authentication → Users*: the scenario's uid now lists the Apple provider; no new user.
4. Sign out, add an item as the new guest, tap "Sign in with Apple" with the same Apple ID.
   - The "Switch to this account?" dialog appears (the same dialog Google uses).
   - "Switch" opens Apple's sheet once more, then shows the data from step 3, not the guest item.

### 13. Deleting an account

*Signed in with a throwaway Google account, as owner of a kitchen with one item in the pantry. For the
shared case, a second device joined to that kitchen (scenario 5).*

1. Profile tab: a red "Delete account" button sits under the account row. It is absent for a guest.
2. Tap it, then "Cancel": nothing changes.
3. Tap it, then "Delete".
   - The app comes back as a fresh guest: empty pantry and list, the profile offers sign-in again.
   - *Authentication → Users*: the account is gone. `users/<uid>` is gone.
   - Alone in the kitchen: `kitchens/<id>`, its `pantry`/`shoppingLists`/`savedRecipes` and its
     `kitchenInvites/<code>` are gone.
   - Shared kitchen: it is still there for the second device, which now shows itself as "Owner".
4. Airplane mode, then "Delete": "No connection" under the button and the account is untouched.

## Results

Fill one row per scenario and platform pass. Leave a cell empty until you have run it; write the
date as `YYYY-MM-DD`; in *Notes* put what you actually saw where it differs from the expectation,
verbatim, and the issue you opened for it. Use *pass*, *fail* or *blocked* in the platform columns.
Do not edit the scenarios to match a result: open an issue.

| Scenario | Android | iOS | Date | Notes |
|---|---|---|---|---|
| 1. First launch |  |  |  |  |
| 2. Google sign-in | fail | fail | 2026-10-13 | Both runs used a personal Google account rather than a throwaway, on an API 36 emulator and an iPhone 17 Pro simulator, device language Spanish. **Android:** steps 2, 3, 5 and 6 as expected: the sheet opened, the row showed the Google name and "Cerrar sesión" with no email or photo, the kitchen showed "(tú)" and "Propietario" with a new join code, and the console had a Google-provider user, `users/<uid>` with the name, a kitchen with `memberIds` of one and the name in `memberDisplayNames`, and one `shoppingLists` document. Step 4 failed: the Pantry tab showed "Esta cuenta no tiene permiso para leer sus propios datos" with the anonymous user's rows (#240, fixed in #241); on a build with the fix, signing out and in again left Pantry and Shopping empty with no error. The Ideas tab showed "La app no ha podido demostrar quién es", the App Check message, because this install's debug token was not registered. Steps 1 and 7 not run. **iOS:** step 1: dismissing the sheet showed the red "Algo ha ido mal" and the button was enabled again, still anonymous. Step 2: the sheet opened after the system prompt and the app came back to the foreground, so the URL scheme works. Step 3 failed: the session stayed anonymous with "Algo ha ido mal" and no request reached Firebase Auth (#242, fixed in #243); on a build with the fix the row showed the Google name and "Cerrar sesión" with no email or photo. Steps 4 to 7 not run on iOS. |
| 3. Sign-out |  |  |  |  |
| 4. Failed account switch, retry |  |  |  |  |
| 5. Two accounts: join and shared data |  |  |  |  |
| 6. Leaving |  |  |  |  |
| 7. Remove member, no rejoin |  |  |  |  |
| 8. Regenerate join code |  |  |  |  |
| 9. Recipe photos by dish type |  |  | 2026-10-09 | Partial, so the platform cells stay empty. On both platforms a generated suggestion showed a bundled photograph on its card and the same one in the detail header, loaded and not stretched; two dish types seen on iOS (grilled, rice dish), one on Android. Steps 1 (pull to generate), 4 and the three-dish-type check of step 5 were not run: the existing generated set was reused. |
| 10. Missing `dish-types` tag |  |  |  |  |
| 11. Hand-typed shopping amount | pass | blocked | 2026-10-12 | Android (API 36 emulator, device language Spanish), not scripted to the letter: step 2 used "Harina de trigo" with `500` g instead of `2` kg and the line showed "500 g" with the amount field empty again; step 6 gave a line `2` of the unit "unidad" instead of `500` g, which moved to the pantry and showed there as "2 unidades". Step 7's message was seen first, with two ticked lines and no amount: "No se ha movido nada: 2 líneas no tienen cantidad. Toca Añadir cantidad para ponerla." Step 4's message followed: "1 movido a la despensa, 1 no tiene cantidad y se queda en la lista". Step 5: "Guardar" was disabled with the field empty. With the keyboard open the name field, the chip suggestion, the amount field and "Añadir" were all visible above it. Steps 1 and 5 checked the keyboard on Android only. iOS: the soft keyboard could not be shown in the simulator (it takes the Mac's hardware keyboard and toggling it needs accessibility permission), so the keyboard checks are blocked; the two-row add field and the rows were seen, steps 2 to 7 were not run. |
