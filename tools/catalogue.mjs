#!/usr/bin/env node
// Writes one catalogue ingredient or recipe through the `writeCatalogue` callable, as an admin
// account. The payload is `{ "kind": "ingredient" | "recipe", "id": "...", "data": { ... } }`.
//
//   node tools/catalogue.mjs --dry-run entry.json          # validate only, no credentials
//   KITCHENAI_ID_TOKEN=<idToken> node tools/catalogue.mjs --project <projectId> entry.json
//
// Without a token it signs in as the admin account itself: Application Default Credentials of a
// service account (or one being impersonated) mint a custom token for --uid, traded for an ID
// token with --api-key. See docs/catalogue-admin.md.

import { readFileSync } from 'node:fs';
import {
  UsageError,
  callableUrl,
  describeFailure,
  describeResult,
  parseCatalogueArgs,
  parsePayload,
  validatePayload,
} from './lib/catalogue-cli.mjs';

const USAGE = `usage: node tools/catalogue.mjs [--dry-run] [--project <projectId>] [--region <region>]
                              [--uid <uid> --api-key <webApiKey>] [file | -]
environment: KITCHENAI_PROJECT, KITCHENAI_REGION, KITCHENAI_ADMIN_UID, KITCHENAI_API_KEY, KITCHENAI_ID_TOKEN`;

/** A custom token for the admin uid, traded for an ID token that carries the stored claims. */
async function signIn(args) {
  if (args.idToken) return args.idToken;
  const [{ applicationDefault, initializeApp }, { getAuth }] = await Promise.all([
    import('firebase-admin/app'),
    import('firebase-admin/auth'),
  ]);
  initializeApp({ credential: applicationDefault(), projectId: args.project });
  let customToken;
  try {
    customToken = await getAuth().createCustomToken(args.uid);
  } catch (failure) {
    throw new Error(`could not mint a custom token (needs service-account credentials): ${failure.message}`);
  }
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${encodeURIComponent(args.apiKey)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: customToken, returnSecureToken: true }),
    },
  );
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body.idToken) throw new Error(`sign-in was refused (HTTP ${response.status})`);
  return body.idToken;
}

async function main() {
  const args = parseCatalogueArgs(process.argv.slice(2), process.env);
  if (args.help) {
    console.log(USAGE);
    return;
  }

  const raw = parsePayload(readFileSync(args.file === '-' ? 0 : args.file, 'utf8'));
  const verdict = await validatePayload(raw);
  if (!verdict.ok) {
    console.error(`invalid payload: ${verdict.message}`);
    process.exit(1);
  }
  if (args.dryRun) {
    console.log(JSON.stringify({ kind: verdict.kind, id: verdict.id, valid: true }));
    return;
  }

  const idToken = await signIn(args);
  const response = await fetch(callableUrl(args.project, args.region), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}` },
    body: JSON.stringify({ data: raw }),
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    console.error(describeFailure(response.status, body));
    process.exit(1);
  }
  console.log(JSON.stringify(body.result));
  console.error(describeResult(body.result));
}

main().catch((failure) => {
  console.error(failure instanceof UsageError ? `${failure.message}\n${USAGE}` : failure.message);
  process.exit(1);
});
