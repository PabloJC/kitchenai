import type { DocumentReference, Firestore } from 'firebase-admin/firestore';
import { logger } from 'firebase-functions';
import type { MealType } from './contract.js';

/**
 * A ceiling on what one account can spend of ours in a day.
 *
 * App Check answers "is this our app?". It does not answer "is our app asking ten thousand
 * times?", and after the Blaze upgrade that second question costs money. Suggestions get one
 * run per meal per day ([withMealSlot]); images a counter ([chargeCall]). Both are checked
 * before the model is called.
 */

/** Five slots of five ideas, one photo each (#395). Stored images are free and not counted. */
export const IMAGE_DAILY_LIMIT = Number(process.env.IMAGE_DAILY_LIMIT ?? 25);

/** One allowance per account per day. [suffix] keeps each counter in its own document. */
export interface DailyCounter {
  suffix: string;
  limit: number;
}

export const IMAGES: DailyCounter = { suffix: '_images', limit: IMAGE_DAILY_LIMIT };

/** A run with no meal type is a slot of its own. */
export type MealSlot = 'breakfast' | 'lunch' | 'dinner' | 'snack' | 'any';

export function mealSlot(mealType: MealType | null): MealSlot {
  return mealType === null ? 'any' : (mealType.toLowerCase() as MealSlot);
}

/** Kept out of client reach by the rules; a counter a caller can edit is not a counter. */
const COLLECTION = 'agentUsage';

/** Long enough to outlive the day it counts, short enough that nothing accumulates forever. */
const KEEP_FOR_DAYS = 3;

/** Past the function's 60 s timeout: a run that died without releasing its slot frees it on its own. */
const CLAIM_MS = 2 * 60 * 1000;

export class QuotaExceeded extends Error {}

/**
 * Days are UTC. A user near midnight gets a slightly odd boundary; the alternative is trusting
 * a timezone the caller sends, which is a field someone would eventually set to whatever gives
 * them a fresh allowance.
 */
function dayOf(now: Date): string {
  return now.toISOString().slice(0, 10);
}

function expiry(now: Date): Date {
  // A Firestore TTL policy on this field clears the collection without a scheduled job.
  return new Date(now.getTime() + KEEP_FOR_DAYS * 24 * 60 * 60 * 1000);
}

/**
 * Counts the call and refuses it if the day is spent. A transaction because two calls arriving
 * together must not each read the same count and both decide they are the first.
 */
export async function chargeCall(db: Firestore, uid: string, now: Date, counter: DailyCounter): Promise<number> {
  const doc = db.collection(COLLECTION).doc(`${uid}_${dayOf(now)}${counter.suffix}`);

  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(doc);
    const used = (snapshot.data()?.calls as number | undefined) ?? 0;
    if (used >= counter.limit) {
      throw new QuotaExceeded(`${used} calls today`);
    }
    transaction.set(doc, { calls: used + 1, expiresAt: expiry(now) });
    return used + 1;
  });
}

/**
 * Runs [run] in the account's one slot for [slot] today. The slot is claimed in a transaction
 * first, so two runs at once make one model call; it is kept only when [run] returns ideas, so a
 * failed or empty run does not cost the user the meal.
 */
export async function withMealSlot<T>(
  db: Firestore,
  uid: string,
  now: Date,
  slot: MealSlot,
  run: () => Promise<T[]>,
): Promise<T[]> {
  const doc = db.collection(COLLECTION).doc(`${uid}_${dayOf(now)}_meal_${slot}`);

  await db.runTransaction(async (transaction) => {
    const data = (await transaction.get(doc)).data();
    if (data?.used === true) throw new QuotaExceeded(`${slot} already has its ideas today`);
    const claimedUntil = data?.claimedUntil as number | undefined;
    // Same answer as a used slot: the run holding the claim is the one that brings today's ideas.
    if (claimedUntil !== undefined && claimedUntil > now.getTime()) {
      throw new QuotaExceeded(`${slot} is already running`);
    }
    transaction.set(doc, { claimedUntil: now.getTime() + CLAIM_MS, expiresAt: expiry(now) });
  });

  let ideas: T[];
  try {
    ideas = await run();
  } catch (failure) {
    await release(doc);
    throw failure;
  }
  if (ideas.length === 0) {
    await release(doc);
  } else {
    await markUsed(doc, now);
  }
  return ideas;
}

/**
 * The answer is already paid for, so a failed write must not lose it. It is retried once and
 * logged, because the claim alone reopens the slot after [CLAIM_MS].
 */
async function markUsed(doc: DocumentReference, now: Date): Promise<void> {
  const write = () => doc.set({ used: true, expiresAt: expiry(now) });
  try {
    await write().catch(write);
  } catch (failure) {
    logger.error('could not mark the meal slot used', { failure: String(failure) });
  }
}

/** A release that fails is not retried: the claim expires after [CLAIM_MS] anyway. */
async function release(doc: DocumentReference): Promise<void> {
  await doc.delete().catch(() => undefined);
}
