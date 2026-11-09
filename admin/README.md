# KitchenAI admin

A web app for curating the shared catalogue: ingredients, recipes, taxonomies, the free-text
queue and usage (#399 and the issues it links). React, Vite and TypeScript, talking to Firebase
directly with the web SDK. Only work that needs privileges or the model goes through a function.

## Who gets in

An account whose ID token carries `admin: true`, set with `tools/grant-admin.mjs`
(`docs/catalogue-admin.md`). The app checks the claim to decide what to render; the Firestore and
Storage rules check it again on every request, and they are what actually protects the data.

## Configuration

```bash
cp .env.example .env.local
```

Fill in the values of the project's web app (Firebase console › Project settings › Your apps).
`.env.local` is ignored by git. The values are read at runtime, so `npm run build` needs none of
them, but a build to deploy does: Vite bakes them in at build time.

## Running against the emulators

From the repository root, with a `demo-` project id so nothing can reach a real backend:

```bash
firebase emulators:start --only auth,firestore,storage,functions --project demo-kitchenai-admin
```

In `.env.local`, set `VITE_FIREBASE_PROJECT_ID=demo-kitchenai-admin`, any non-empty value for the
other variables, and `VITE_USE_EMULATORS=true`. Then:

```bash
npm install
npm run dev
```

The Google button opens the emulator's sign-in page. To give an emulator account the claim, add it
in the emulator UI (Authentication › the account › Custom claims: `{"admin": true}`) and sign in
again.

## Checks

```bash
npm run lint   # type check, ESLint, Prettier
npm test       # Vitest
npm run build
```

CI runs the same three in the "Admin (lint, build, tests)" job.

## Deploying

The admin is its own Hosting site, so deploying it never touches the privacy and account-deletion
pages. Once per checkout, create the site in the console (Hosting › Add another site) and map it:

```bash
firebase target:apply hosting admin <adminSiteId> --project <projectId>
```

Then, with `.env.local` filled in for that project:

```bash
firebase deploy --only hosting:admin --project <projectId>
```

The predeploy step builds `admin/dist`. Add the site's domain to Authentication › Settings ›
Authorized domains, or Google sign-in is refused.
