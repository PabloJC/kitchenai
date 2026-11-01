// Run by `firebase deploy --only hosting` before uploading (firebase.json › hosting.predeploy):
// a policy published with a placeholder where the contact should be is worse than none.
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { findPlaceholders } from './lib/hosting-checks.mjs';

const dir = fileURLToPath(new URL('../hosting/', import.meta.url));
const pages = Object.fromEntries(
  readdirSync(dir).filter((name) => name.endsWith('.html')).map((name) => [name, readFileSync(dir + name, 'utf8')]),
);
const problems = findPlaceholders(pages);
if (problems.length > 0) {
  for (const problem of problems) console.error(problem);
  console.error('Replace CONTACT_EMAIL_PLACEHOLDER in hosting/ with the real contact address before deploying.');
  process.exit(1);
}
console.log(`hosting: ${Object.keys(pages).length} pages, no placeholder left.`);
