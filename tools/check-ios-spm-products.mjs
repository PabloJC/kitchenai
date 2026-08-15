// Every GitLive Firebase artefact `:shared` depends on needs its native counterpart registered
// in the Xcode project by hand. Miss one and the Kotlin framework still links, every job here
// goes green, and the app fails inside Xcode with undefined symbols for arm64.
//
// This asserts the invariant instead of paying for a full iOS build to rediscover it. It cannot
// tell you the app compiles; it tells you nobody forgot this particular step.

import { readFileSync } from 'node:fs';

const GRADLE = 'shared/build.gradle.kts';
const PBXPROJ = 'iosApp/iosApp.xcodeproj/project.pbxproj';

// Not derived from the artefact name: `app` and `common` are both served by FirebaseCore, so
// there is no rule to infer. An artefact missing from here fails too — see below.
const NATIVE_PRODUCT = {
  app: 'FirebaseCore',
  common: 'FirebaseCore',
  auth: 'FirebaseAuth',
  firestore: 'FirebaseFirestore',
  functions: 'FirebaseFunctions',
  storage: 'FirebaseStorage',
  messaging: 'FirebaseMessaging',
  database: 'FirebaseDatabase',
  'crashlytics': 'FirebaseCrashlytics',
  'perf': 'FirebasePerformance',
  'installations': 'FirebaseInstallations',
  'config': 'FirebaseRemoteConfig',
};

// The dependency block, not the version catalogue: the catalogue declares artefacts nobody uses,
// and an unused one needs no native product.
const used = [...readFileSync(GRADLE, 'utf8').matchAll(/libs\.gitlive\.firebase\.(\w+)/g)].map((m) => m[1]);
const declared = readFileSync(PBXPROJ, 'utf8');

const unknown = used.filter((name) => !(name in NATIVE_PRODUCT));
const missing = [...new Set(used)]
  .filter((name) => name in NATIVE_PRODUCT)
  .map((name) => [name, NATIVE_PRODUCT[name]])
  // The trailing semicolon matters: without it `FirebaseCore` is satisfied by a line declaring
  // `FirebaseCoreExtension`, and the check passes on a product nobody added.
  .filter(([, product]) => !declared.includes(`productName = ${product};`));

if (unknown.length > 0) {
  // Loud rather than silent: an artefact this file has never heard of is exactly the case the
  // check exists for, and passing it would defeat the whole thing.
  console.error(`Unknown GitLive artefact: ${[...new Set(unknown)].join(', ')}`);
  console.error(`Add it to NATIVE_PRODUCT in ${import.meta.filename ?? 'tools/check-ios-spm-products.mjs'},`);
  console.error('naming the Firebase SPM product it needs, then add that product in Xcode.');
}

for (const [name, product] of missing) {
  console.error(`dev.gitlive:firebase-${name} needs the ${product} SPM product in the iOS app.`);
  console.error(`  Xcode → iosApp target → Frameworks, Libraries → + → firebase-ios-sdk → ${product}`);
}

if (unknown.length > 0 || missing.length > 0) process.exit(1);

console.log(`iOS SPM products present for ${new Set(used).size} GitLive artefacts.`);
