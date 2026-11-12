import type { DocumentReference, Firestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';
import { type AdminCall, isAdmin } from './catalogueAdmin.ts';
import { REQUESTS, freeTextKey, requestId } from './ingredientRequests.ts';

// Under Firestore's 500 writes per batch, with room to spare.
const BATCH_SIZE = 400;
const REQUEST_ID = /^[0-9a-f]{32}$/;
const PANTRY_ITEM = /^kitchens\/[^/]+\/pantry\/[^/]+$/;
const SHOPPING_ITEM = /^kitchens\/[^/]+\/shoppingLists\/[^/]+\/items\/[^/]+$/;

export interface MigrationResult {
  pantryItems: number;
  shoppingItems: number;
  dryRun: boolean;
}

/** What the handler needs besides the database: Firestore's delete sentinel and the clock, so a test can supply both. */
export interface MigrationDeps {
  deleteField: unknown;
  nowMillis: number;
}

/** `{ requestId, dryRun? }`: the request is named by its document id, never by its text. */
export function parseMigration(data: unknown): { requestId: string; dryRun: boolean } {
  if (data === null || typeof data !== 'object') throw new HttpsError('invalid-argument', 'malformed request');
  const { requestId: id, dryRun, ...rest } = data as Record<string, unknown>;
  if (Object.keys(rest).length > 0) throw new HttpsError('invalid-argument', 'unknown field');
  if (typeof id !== 'string' || !REQUEST_ID.test(id)) throw new HttpsError('invalid-argument', 'requestId must be a request id');
  if (dryRun !== undefined && typeof dryRun !== 'boolean') throw new HttpsError('invalid-argument', 'dryRun must be a boolean');
  return { requestId: id, dryRun: dryRun ?? false };
}

/**
 * Rewrites every pantry and shopping item whose free text is the request's onto its catalogue
 * ingredient (#405). The admin never reads a kitchen: this runs with the Admin SDK and returns counts.
 * Items are compared by the queue's own key, so it finds every spelling the request counted.
 */
export async function migrateFreeText(db: Firestore, call: AdminCall, deps: MigrationDeps): Promise<MigrationResult> {
  if (!call.auth) throw new HttpsError('unauthenticated', 'sign in first');
  if (!isAdmin(call.auth)) throw new HttpsError('permission-denied', 'admin only');
  const { requestId: id, dryRun } = parseMigration(call.data);

  const requestRef = db.collection(REQUESTS).doc(id);
  const request = await requestRef.get();
  if (!request.exists) throw new HttpsError('failed-precondition', 'no such request');
  const status = request.get('status');
  const ingredientId = request.get('ingredientId');
  if ((status !== 'promoted' && status !== 'aliased') || typeof ingredientId !== 'string') {
    throw new HttpsError('failed-precondition', 'only a promoted or aliased request can be converted');
  }
  const ingredient = await db.collection('ingredients').doc(ingredientId).get();
  if (!ingredient.exists || ingredient.get('archived') === true) {
    throw new HttpsError('failed-precondition', 'the ingredient is missing or archived');
  }

  const [pantry, shopping] = await Promise.all([
    matchingItems(db, 'pantry', PANTRY_ITEM, id),
    matchingItems(db, 'items', SHOPPING_ITEM, id),
  ]);
  const result = { pantryItems: pantry.length, shoppingItems: shopping.length, dryRun };
  if (dryRun) return result;

  const change = { ingredientId, freeText: deps.deleteField, updatedAtMillis: deps.nowMillis };
  const items = [...pantry, ...shopping];
  for (let start = 0; start < items.length; start += BATCH_SIZE) {
    const batch = db.batch();
    for (const item of items.slice(start, start + BATCH_SIZE)) batch.update(item, change);
    await batch.commit();
  }
  // The triggers also decrement as each item loses its text; they stop at zero.
  await requestRef.update({ activeCount: 0, migratedAtMillis: deps.nowMillis });
  return result;
}

/**
 * Every free-text item of one collection group whose text keys to the request. Reads all free-text
 * items of the group: the key is not stored on items, so it cannot be queried for.
 */
async function matchingItems(db: Firestore, group: string, path: RegExp, id: string): Promise<DocumentReference[]> {
  const snapshot = await db.collectionGroup(group).where('freeText', '>=', '').get();
  return snapshot.docs
    .filter((doc) => path.test(doc.ref.path))
    .filter((doc) => {
      const key = freeTextKey(doc.get('freeText'));
      return key !== null && requestId(key) === id;
    })
    .map((doc) => doc.ref);
}
