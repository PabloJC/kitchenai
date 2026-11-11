#!/usr/bin/env node
// Builds the anonymous ingredient-request queue (#402) once, from the free text already in pantries
// and shopping lists. Dry run unless --write; prints counts only, never anyone's text.
//
//   gcloud auth application-default login
//   node tools/backfill-ingredient-requests.mjs --project <projectId>            # what it would do
//   node tools/backfill-ingredient-requests.mjs --project <projectId> --write    # do it

import { REQUESTS, UsageError, isFreeTextItemPath, parseBackfillArgs, planBackfill } from './lib/ingredient-requests-backfill.mjs';

const USAGE = 'usage: node tools/backfill-ingredient-requests.mjs --project <projectId> [--write]';
const BATCH = 400;

async function main() {
  const args = parseBackfillArgs(process.argv.slice(2), process.env);
  if (args.help) {
    console.log(USAGE);
    return;
  }
  const [{ applicationDefault, initializeApp }, { getFirestore }] = await Promise.all([
    import('firebase-admin/app'),
    import('firebase-admin/firestore'),
  ]);
  initializeApp({ credential: applicationDefault(), projectId: args.project });
  const db = getFirestore();

  const [pantry, shopping, queue] = await Promise.all([
    db.collectionGroup('pantry').get(),
    db.collectionGroup('items').get(),
    db.collection(REQUESTS).get(),
  ]);
  const items = [...pantry.docs, ...shopping.docs]
    .filter((doc) => isFreeTextItemPath(doc.ref.path))
    .map((doc) => ({ path: doc.ref.path, freeText: doc.get('freeText'), updatedAtMillis: doc.get('updatedAtMillis') }));
  const existing = new Map(queue.docs.map((doc) => [doc.id, doc.data()]));
  const writes = planBackfill(items, existing, Date.now());

  const created = [...writes.keys()].filter((id) => !existing.has(id)).length;
  console.log(
    `${items.length} items scanned, ${existing.size} requests found; ` +
      `${created} to create, ${writes.size - created} to update.`,
  );
  if (!args.write) {
    console.log('Dry run: nothing written. Add --write to apply.');
    return;
  }
  const entries = [...writes];
  for (let start = 0; start < entries.length; start += BATCH) {
    const batch = db.batch();
    for (const [id, document] of entries.slice(start, start + BATCH)) batch.set(db.collection(REQUESTS).doc(id), document);
    await batch.commit();
  }
  console.log(`${writes.size} requests written.`);
}

main().catch((failure) => {
  console.error(failure instanceof UsageError ? `${failure.message}\n${USAGE}` : failure.message);
  process.exit(1);
});
