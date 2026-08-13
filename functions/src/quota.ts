import type { Firestore } from 'firebase-admin/firestore';

/**
 * A ceiling on what one account can spend of ours in a day.
 *
 * App Check answers "is this our app?". It does not answer "is our app asking ten thousand
 * times?", and after the Blaze upgrade that second question costs money. This is the answer to
 * it: a counter per account per day, checked before the model is called.
 */
export const DAILY_CALL_LIMIT = Number(process.env.DAILY_CALL_LIMIT ?? 50);

/** Kept out of client reach by the rules; a counter a caller can edit is not a counter. */
const COLLECTION = 'agentUsage';

/** Long enough to outlive the day it counts, short enough that nothing accumulates forever. */
const KEEP_FOR_DAYS = 3;

export class QuotaExceeded extends Error {}

/**
 * Counts the call and refuses it if the day is spent. A transaction because two calls arriving
 * together must not each read the same count and both decide they are the first.
 *
 * Days are UTC. A user near midnight gets a slightly odd boundary; the alternative is trusting
 * a timezone the caller sends, which is a field someone would eventually set to whatever gives
 * them a fresh allowance.
 */
export async function chargeCall(db: Firestore, uid: string, now: Date): Promise<number> {
  const day = now.toISOString().slice(0, 10);
  const doc = db.collection(COLLECTION).doc(`${uid}_${day}`);

  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(doc);
    const used = (snapshot.data()?.calls as number | undefined) ?? 0;
    if (used >= DAILY_CALL_LIMIT) {
      throw new QuotaExceeded(`${used} calls today`);
    }
    transaction.set(doc, {
      calls: used + 1,
      // A Firestore TTL policy on this field clears the collection without a scheduled job.
      expiresAt: new Date(now.getTime() + KEEP_FOR_DAYS * 24 * 60 * 60 * 1000),
    });
    return used + 1;
  });
}
