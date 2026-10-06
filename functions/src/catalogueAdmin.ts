import type { Firestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';
import { COLLECTIONS, parseCatalogueEntry } from './catalogueEntry.ts';
import type { CatalogueEntry } from './catalogueEntry.ts';
import { BadRequest } from './contract.ts';

/** The slice of a callable request this handler reads; `CallableRequest` satisfies it. */
export interface AdminCall {
  data: unknown;
  auth?: { token: Record<string, unknown> } | undefined;
}

/** `true` exactly: a truthy string or number in the claim must not pass. */
export function isAdmin(auth: AdminCall['auth']): boolean {
  return auth?.token.admin === true;
}

/**
 * Writes one catalogue document by id, as `tools/seed.mjs` does per document: `set` without
 * merge, so the document becomes exactly the payload and writing twice leaves the same database.
 * An existing id is replaced, not refused; `created` says which happened.
 */
export async function writeCatalogueEntry(
  db: Firestore,
  call: AdminCall,
): Promise<{ kind: string; id: string; created: boolean }> {
  if (!call.auth) throw new HttpsError('unauthenticated', 'sign in first');
  if (!isAdmin(call.auth)) throw new HttpsError('permission-denied', 'admin only');

  let entry: CatalogueEntry;
  try {
    entry = parseCatalogueEntry(call.data);
  } catch (failure) {
    // Only our own message is echoed, never the caller's text.
    throw new HttpsError('invalid-argument', failure instanceof BadRequest ? failure.message : 'malformed request');
  }

  const ref = db.collection(COLLECTIONS[entry.kind]).doc(entry.id);
  const created = await db.runTransaction(async (transaction) => {
    const existing = await transaction.get(ref);
    transaction.set(ref, entry.document);
    return !existing.exists;
  });
  return { kind: entry.kind, id: entry.id, created };
}
