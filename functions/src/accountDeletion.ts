import type { DocumentReference, Firestore } from 'firebase-admin/firestore';
import { HttpsError } from 'firebase-functions/v2/https';

/** The fields of `kitchens/{kitchenId}` this module reads; the app's `KitchenDto` writes them. */
export interface KitchenDoc {
  ownerId?: string;
  memberIds?: string[];
  joinCode?: string;
  memberDisplayNames?: Record<string, string>;
}

/** What deleting one account does to one of its kitchens. */
export type KitchenPlan =
  | { kind: 'delete'; joinCode: string | undefined }
  | { kind: 'leave'; update: Required<Pick<KitchenDoc, 'ownerId' | 'memberIds' | 'memberDisplayNames'>> }
  | { kind: 'skip' };

/**
 * A kitchen the account was alone in goes with it. One it shared stays for the others; if the
 * account owned it, the member who joined first after it becomes the owner, since the app has no
 * other way to hand a kitchen over and an ownerless one could never be managed again.
 */
export function planKitchen(uid: string, kitchen: KitchenDoc | undefined): KitchenPlan {
  const members = kitchen?.memberIds ?? [];
  if (!kitchen || !members.includes(uid)) return { kind: 'skip' };
  const remaining = members.filter((member) => member !== uid);
  if (remaining.length === 0) return { kind: 'delete', joinCode: kitchen.joinCode };

  const names = { ...(kitchen.memberDisplayNames ?? {}) };
  delete names[uid];
  const ownerId = kitchen.ownerId === uid || !kitchen.ownerId ? remaining[0]! : kitchen.ownerId;
  return { kind: 'leave', update: { ownerId, memberIds: remaining, memberDisplayNames: names } };
}

/** The slice of the Admin Auth SDK this needs, so a test can stand in for it. */
export interface AccountAuth {
  deleteUser(uid: string): Promise<void>;
}

/** The slice of a callable request this handler reads; `CallableRequest` satisfies it. */
export interface AccountCall {
  auth?: { uid: string } | undefined;
}

export interface DeletionResult {
  deletedKitchens: number;
  leftKitchens: number;
}

/**
 * Deletes the caller's account: their kitchens as `planKitchen` decides, their profile, then the
 * Auth user last, so a failure half-way leaves an account that can simply ask again. Every step
 * is idempotent for that reason, including an Auth user that is already gone.
 *
 * `agentUsage/{uid}_{day}` is left to its TTL policy (three days): it is a counter, not content.
 */
export async function deleteAccount(db: Firestore, auth: AccountAuth, call: AccountCall): Promise<DeletionResult> {
  const uid = call.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'sign in first');

  const kitchens = await db.collection('kitchens').where('memberIds', 'array-contains', uid).get();
  let deletedKitchens = 0;
  let leftKitchens = 0;
  for (const doc of kitchens.docs) {
    const plan = await applyPlan(db, uid, doc.ref);
    if (plan.kind === 'delete') deletedKitchens++;
    if (plan.kind === 'leave') leftKitchens++;
  }

  await db.collection('users').doc(uid).delete();
  try {
    await auth.deleteUser(uid);
  } catch (failure) {
    if ((failure as { code?: string }).code !== 'auth/user-not-found') throw failure;
  }
  return { deletedKitchens, leftKitchens };
}

/**
 * Re-reads the kitchen in a transaction: someone may have joined or left since the query. A
 * kitchen to delete loses its invite in that transaction, so no one can join it while its
 * subcollections are being removed.
 */
async function applyPlan(db: Firestore, uid: string, ref: DocumentReference): Promise<KitchenPlan> {
  const plan = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const decided = planKitchen(uid, snapshot.data() as KitchenDoc | undefined);
    if (decided.kind === 'leave') transaction.set(ref, decided.update, { merge: true });
    if (decided.kind === 'delete' && decided.joinCode) {
      transaction.delete(db.collection('kitchenInvites').doc(decided.joinCode));
    }
    return decided;
  });
  if (plan.kind === 'delete') await db.recursiveDelete(ref);
  return plan;
}
