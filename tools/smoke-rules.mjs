#!/usr/bin/env node
// Replays the kitchen writes the app makes on first launch against a DEPLOYED project, through the
// Firestore REST API with two throwaway anonymous users. It exists because the rules tests run on
// the emulator, which does not behave like production inside one atomic write (see
// firebase/README.md), and because nothing else notices a deployment that lags the code.
//
//   KITCHENAI_PROJECT=<projectId> KITCHENAI_API_KEY=<key> node tools/smoke-rules.mjs
//   node tools/smoke-rules.mjs --config androidApp/google-services.json --cert-sha1 <debug-sha1>
//
// Client rules cannot delete a kitchen, so the documents stay behind unless --cleanup is given.
// That deletes exactly the documents this run wrote, with your Application Default Credentials:
//   gcloud auth application-default login   (and `npm install` in tools/)

import { randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import {
  STALE_DEPLOY_HINT,
  UsageError,
  assertCleanable,
  buildSteps,
  commitBody,
  evaluate,
  fillFromGoogleServices,
  formatLine,
  parseSmokeArgs,
  redact,
  resolveTarget,
  runIds,
  touchedPaths,
} from './lib/smoke-rules.mjs';

const USAGE =
  'usage: node tools/smoke-rules.mjs [--project <id>] [--api-key <key>] [--config <google-services.json>]\n' +
  '                                  [--android-package <pkg>] [--cert-sha1 <sha1>] [--cleanup]\n' +
  'environment: KITCHENAI_PROJECT, KITCHENAI_API_KEY, KITCHENAI_ANDROID_PACKAGE, KITCHENAI_CERT_SHA1,\n' +
  '             KITCHENAI_GOOGLE_SERVICES; FIRESTORE_EMULATOR_HOST + FIREBASE_AUTH_EMULATOR_HOST for an emulator';

const TIMEOUT_MS = 30_000;

// Filled once the target is known; anything printed after that goes through `redact`.
let secrets = [];

async function post(url, body, headers) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  return readResponse(response);
}

async function get(url, headers) {
  return readResponse(await fetch(url, { headers, signal: AbortSignal.timeout(TIMEOUT_MS) }));
}

async function readResponse(response) {
  const text = await response.text();
  try {
    return { status: response.status, body: JSON.parse(text) };
  } catch {
    return { status: response.status, body: { raw: text.slice(0, 200) } };
  }
}

/** A fresh anonymous account, exactly what the app's session gate creates. */
async function signIn(target) {
  const { status, body } = await post(
    `${target.identityBase}/accounts:signUp?key=${target.apiKey}`,
    { returnSecureToken: true },
    target.androidHeaders,
  );
  if (status !== 200) {
    throw new Error(`anonymous sign-in failed: HTTP ${status} ${body?.error?.message ?? ''}`.trim());
  }
  return { uid: body.localId, idToken: body.idToken };
}

/** Best effort: the account is the script's own, and an anonymous user may delete itself. */
async function deleteAccount(target, user) {
  const { status } = await post(
    `${target.identityBase}/accounts:delete?key=${target.apiKey}`,
    { idToken: user.idToken },
    target.androidHeaders,
  );
  return status === 200;
}

function send(target, actor, op) {
  const headers = { Authorization: `Bearer ${actor.idToken}` };
  return op.type === 'commit'
    ? post(`${target.firestoreBase}:commit`, commitBody(target.project, op.writes), headers)
    : get(`${target.firestoreBase}/${op.path}`, headers);
}

/** Deletes exactly the paths this run wrote, as the Admin SDK: client rules cannot delete a kitchen. */
async function cleanUp(target, paths) {
  paths.forEach(assertCleanable);
  let admin;
  try {
    admin = { app: await import('firebase-admin/app'), firestore: await import('firebase-admin/firestore') };
  } catch {
    throw new Error('--cleanup needs firebase-admin: run `npm install` in tools/');
  }
  admin.app.initializeApp({ credential: admin.app.applicationDefault(), projectId: target.project });
  const db = admin.firestore.getFirestore();
  for (const path of paths) await db.doc(path).delete();
  console.log(`cleaned up ${paths.length} documents`);
}

async function main() {
  const args = parseSmokeArgs(process.argv.slice(2), process.env);
  if (args.help) {
    console.log(USAGE);
    return 0;
  }
  const config = args.config ? JSON.parse(readFileSync(args.config, 'utf8')) : null;
  const target = resolveTarget(fillFromGoogleServices(args, config));
  secrets = [target.project, target.apiKey];

  console.log(target.emulated ? 'target: emulator' : 'target: deployed project');
  // Inside the try so a failure after the first sign-in still deletes the account it created.
  const accounts = [];
  let paths = [];
  let failed = false;

  try {
    const owner = await signIn(target);
    accounts.push(owner);
    const member = await signIn(target);
    accounts.push(member);
    console.log('ok   -- two throwaway anonymous users signed in');

    const steps = buildSteps(runIds(randomBytes(4).toString('hex'), owner.uid, member.uid));
    const actors = { owner, member };
    paths = touchedPaths(steps);

    for (const [index, step] of steps.entries()) {
      let verdict;
      try {
        verdict = evaluate(step, await send(target, actors[step.actor], step.op));
      } catch (failure) {
        verdict = { ok: false, detail: `request failed: ${redact(failure.message, secrets)}` };
      }
      console.log(redact(formatLine(index + 1, step, verdict), secrets));
      if (!verdict.ok) {
        failed = true;
        if (index === 0) console.log(STALE_DEPLOY_HINT);
        // Later steps build on earlier ones, so a surprise early on makes the rest meaningless.
        console.log(`stopped: ${steps.length - index - 1} steps not run`);
        break;
      }
    }
  } finally {
    const removed = await Promise.all(accounts.map((account) => deleteAccount(target, account))).catch(() => []);
    console.log(
      accounts.length > 0 && removed.length === accounts.length && removed.every(Boolean)
        ? 'ok   -- throwaway users deleted'
        : 'warn -- could not delete the throwaway anonymous users',
    );
  }

  report(paths, target.cleanup);
  // The verdict is about the steps; a cleanup problem is reported beside it, never instead of it.
  console.log(failed ? 'SMOKE FAILED' : 'SMOKE PASSED');
  if (target.cleanup) {
    try {
      await cleanUp(target, paths);
    } catch (failure) {
      console.log(`warn -- cleanup did not run: ${redact(failure.message, secrets)}`);
    }
  }
  return failed ? 1 : 0;
}

function report(paths, cleaning) {
  console.log(`documents written by this run (${paths.length}), every id starts with "smoke-":`);
  paths.forEach((path) => console.log(`  ${path}`));
  if (!cleaning) {
    console.log('client rules cannot delete them: remove them in the console, or rerun with --cleanup');
  }
}

main().then(
  (code) => process.exit(code),
  (failure) => {
    console.error(redact(failure instanceof UsageError ? `${failure.message}\n${USAGE}` : failure.message, secrets));
    process.exit(failure instanceof UsageError ? 2 : 1);
  },
);
