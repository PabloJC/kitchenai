# KitchenAI admin

The web app for curating the shared catalogue: ingredients, recipes, taxonomies, the free-text
queue and usage (#399 and the issues it links). A Kotlin/JS module that reuses `:shared` and talks
to Firebase directly through GitLive, like the apps. The UI is Compose HTML: real DOM, so text
selection, accessibility and file inputs work as on any page. Only work that needs privileges or
the model goes through a function.

## Who gets in

An account whose ID token carries `admin: true`, set with `tools/grant-admin.mjs`
(`docs/catalogue-admin.md`). The app checks the claim to decide what to render; the Firestore and
Storage rules check it again on every request, and they are what protects the data.

## Configuration

Nothing identifying the project is committed.

- **Deployed**, the app reads `/__/firebase/init.json`, which Firebase Hosting serves for the
  project it is deployed to.
- **Locally** (`localhost`), it reads `src/jsMain/resources/firebase-config.local.json`, which git
  ignores. Copy `firebase-config.local.json.example` there and fill it in from the Firebase console
  (Project settings › Your apps › Web), or for the emulators see below.

## Running against the emulators

From the repository root, with a `demo-` project id so nothing can reach a real backend:

```bash
firebase emulators:start --only auth,firestore,storage,functions --project demo-kitchenai-admin
```

In the local config, set `"projectId": "demo-kitchenai-admin"`, any non-empty value for the other
fields, and `"useEmulators": true`. Then:

```bash
./gradlew :admin:jsBrowserDevelopmentRun --continuous
```

The page is at `http://localhost:5173` (8080 is the Firestore emulator). The Google button opens
the emulator's sign-in page. To make an emulator account an admin, add `{"admin": true}` as its
custom claims in the emulator UI (Authentication) and sign in again.

## Checks

```bash
./gradlew :admin:check :admin:jsBrowserDistribution
```

`check` runs the tests on Node, ktlint and detekt. CI runs the same in the "Tests (JVM)" job.

## Deploying

The admin is its own Hosting site, so deploying it never touches the privacy and account-deletion
pages. Once per checkout, create the site in the console (Hosting › Add another site) and map it:

```bash
firebase target:apply hosting admin <adminSiteId> --project <projectId>
```

Then:

```bash
firebase deploy --only hosting:admin --project <projectId>
```

The predeploy step builds `build/dist/js/productionExecutable`. Add the site's domain to
Authentication › Settings › Authorized domains, or Google sign-in is refused.
