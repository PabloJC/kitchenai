#!/usr/bin/env node
// Sets (or with --revoke, clears) the `admin` custom claim that `writeCatalogue` checks. Run it
// once per admin account with your own Application Default Credentials:
//
//   gcloud auth application-default login
//   node tools/grant-admin.mjs --project <projectId> --uid <uid>

import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { UsageError, parseGrantArgs } from './lib/catalogue-cli.mjs';

const USAGE =
  'usage: node tools/grant-admin.mjs --project <projectId> --uid <uid> [--revoke]\n' +
  'environment: KITCHENAI_PROJECT, KITCHENAI_ADMIN_UID';

async function main() {
  const args = parseGrantArgs(process.argv.slice(2), process.env);
  if (args.help) {
    console.log(USAGE);
    return;
  }

  initializeApp({ credential: applicationDefault(), projectId: args.project });
  const auth = getAuth();
  // setCustomUserClaims replaces the whole object, so keep whatever else the account carries.
  const { customClaims } = await auth.getUser(args.uid);
  const { admin: _previous, ...rest } = customClaims ?? {};
  const next = args.revoke ? rest : { ...rest, admin: true };
  await auth.setCustomUserClaims(args.uid, Object.keys(next).length === 0 ? null : next);

  console.log(args.revoke ? 'admin claim removed.' : 'admin claim set.');
  console.log('The account must sign in again (or refresh its ID token) for the change to take effect.');
}

main().catch((failure) => {
  console.error(failure instanceof UsageError ? `${failure.message}\n${USAGE}` : failure.message);
  process.exit(1);
});
