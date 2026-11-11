import { createHash } from 'node:crypto';
import type { Firestore } from 'firebase-admin/firestore';

/**
 * The anonymous queue of what people type by hand (#402). A document holds the text and counts and
 * nothing else: no kitchen, no user, no amount. The admin reads it to grow the catalogue.
 */
export const REQUESTS = 'ingredientRequests';

export type RequestStatus = 'open' | 'promoted' | 'aliased' | 'dismissed';

export interface IngredientRequest {
  text: string;
  /** How many items were ever written with it. */
  count: number;
  /** How many items carry it now: what the free-text migration (#405) will rewrite. */
  activeCount: number;
  firstSeenMillis: number;
  lastSeenMillis: number;
  status: RequestStatus;
  ingredientId?: string;
}

// Same ceiling as `freeText` in firestore.rules.
const MAX_TEXT = 200;

/** What is stored and shown: the text as first typed, trimmed and with single spaces. */
export function displayText(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  const text = raw.normalize('NFC').trim().replace(/\s+/g, ' ');
  return text.length === 0 ? null : text.slice(0, MAX_TEXT);
}

/** What two texts must share to be one request: case and accents do not count. */
export function freeTextKey(raw: unknown): string | null {
  const text = displayText(raw);
  return text === null ? null : text.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
}

/** The document id: a hash, so the id never repeats the text and any text is a valid id. */
export function requestId(key: string): string {
  return createHash('sha256').update(key).digest('hex').slice(0, 32);
}

/** The free text an item gained and lost in one write, as display texts; null when nothing counts. */
export interface FreeTextChange {
  added: string | null;
  removed: string | null;
}

export function freeTextChange(before: unknown, after: unknown): FreeTextChange | null {
  const old = displayText(freeTextOf(before));
  const next = displayText(freeTextOf(after));
  if (freeTextKey(old) === freeTextKey(next)) return null;
  return { added: next, removed: old };
}

function freeTextOf(item: unknown): unknown {
  return item !== null && typeof item === 'object' ? (item as Record<string, unknown>).freeText : undefined;
}

/**
 * Applies one item's change in a transaction. Reads every document before writing, as Firestore
 * requires; never touches `status` or `ingredientId`, so a closed request stays closed.
 */
export async function recordFreeTextChange(db: Firestore, change: FreeTextChange, nowMillis: number): Promise<void> {
  const refOf = (text: string) => db.collection(REQUESTS).doc(requestId(freeTextKey(text)!));
  const added = change.added === null ? null : refOf(change.added);
  const removed = change.removed === null ? null : refOf(change.removed);
  await db.runTransaction(async (transaction) => {
    const addedSnap = added === null ? null : await transaction.get(added);
    const removedSnap = removed === null ? null : await transaction.get(removed);
    if (added !== null && addedSnap !== null) {
      if (addedSnap.exists) {
        transaction.update(added, {
          count: numberOr(addedSnap.get('count')) + 1,
          activeCount: numberOr(addedSnap.get('activeCount')) + 1,
          lastSeenMillis: nowMillis,
        });
      } else {
        const request: IngredientRequest = {
          text: change.added!,
          count: 1,
          activeCount: 1,
          firstSeenMillis: nowMillis,
          lastSeenMillis: nowMillis,
          status: 'open',
        };
        transaction.set(added, request);
      }
    }
    if (removed !== null && removedSnap?.exists) {
      const active = numberOr(removedSnap.get('activeCount'));
      if (active > 0) transaction.update(removed, { activeCount: active - 1 });
    }
  });
}

function numberOr(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}
