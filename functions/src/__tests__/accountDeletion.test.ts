import assert from 'node:assert/strict';
import { test } from 'node:test';
import { deleteAccount, planKitchen, type KitchenDoc } from '../accountDeletion.ts';

test('a kitchen the account was alone in is deleted with its invite code', () => {
  assert.deepEqual(planKitchen('ana', { ownerId: 'ana', memberIds: ['ana'], joinCode: 'abc' }), {
    kind: 'delete',
    joinCode: 'abc',
  });
});

test('a shared kitchen the account owned passes to the member who joined first', () => {
  const plan = planKitchen('ana', {
    ownerId: 'ana',
    memberIds: ['ana', 'bea', 'carl'],
    memberDisplayNames: { ana: 'Ana', bea: 'Bea' },
  });
  assert.deepEqual(plan, {
    kind: 'leave',
    update: { ownerId: 'bea', memberIds: ['bea', 'carl'], memberDisplayNames: { bea: 'Bea' } },
  });
});

test('a shared kitchen the account only belonged to keeps its owner', () => {
  const plan = planKitchen('bea', { ownerId: 'ana', memberIds: ['ana', 'bea'], memberDisplayNames: { bea: 'Bea' } });
  assert.deepEqual(plan, {
    kind: 'leave',
    update: { ownerId: 'ana', memberIds: ['ana'], memberDisplayNames: {} },
  });
});

test('a kitchen the account already left, or one that is gone, is skipped', () => {
  assert.deepEqual(planKitchen('ana', { ownerId: 'bea', memberIds: ['bea'] }), { kind: 'skip' });
  assert.deepEqual(planKitchen('ana', undefined), { kind: 'skip' });
});

/** Documents by path; a recursive delete removes everything under a path. */
function fakeDb(seed: Record<string, Record<string, unknown>>) {
  const store = new Map(Object.entries(seed));
  const ref = (path: string) => ({ path, id: path.split('/').pop()! });
  const collection = (name: string) => ({
    doc: (id: string) => ({ ...ref(`${name}/${id}`), delete: async () => void store.delete(`${name}/${id}`) }),
    where: (field: string, _op: string, value: string) => ({
      get: async () => ({
        docs: [...store.entries()]
          .filter(([path, data]) => path.split('/').length === 2 && path.startsWith(`${name}/`))
          .filter(([, data]) => ((data[field] as string[] | undefined) ?? []).includes(value))
          .map(([path]) => ({ ref: ref(path) })),
      }),
    }),
  });
  return {
    store,
    collection,
    runTransaction: async <T>(body: (t: unknown) => Promise<T>) =>
      body({
        get: async (r: { path: string }) => ({ data: () => store.get(r.path) }),
        // Firestore's merge is deep: a nested map absent from the write keeps its stored keys.
        set: (r: { path: string }, value: Record<string, unknown>, options?: { merge?: boolean }) =>
          store.set(r.path, options?.merge ? deepMerge(store.get(r.path) ?? {}, value) : value),
        // update replaces each top-level field it names.
        update: (r: { path: string }, value: Record<string, unknown>) =>
          store.set(r.path, { ...(store.get(r.path) ?? {}), ...value }),
        delete: (r: { path: string }) => store.delete(r.path),
      }),
    recursiveDelete: async (r: { path: string }) => {
      for (const path of [...store.keys()]) if (path === r.path || path.startsWith(`${r.path}/`)) store.delete(path);
    },
  };
}

function deepMerge(stored: Record<string, unknown>, value: Record<string, unknown>): Record<string, unknown> {
  const merged: Record<string, unknown> = { ...stored };
  for (const [key, next] of Object.entries(value)) {
    const previous = merged[key];
    const bothMaps = [previous, next].every((v) => typeof v === 'object' && v !== null && !Array.isArray(v));
    merged[key] = bothMaps ? deepMerge(previous as Record<string, unknown>, next as Record<string, unknown>) : next;
  }
  return merged;
}

function fakeAuth(failure?: { code: string }) {
  const deleted: string[] = [];
  return {
    deleted,
    deleteUser: async (uid: string) => {
      if (failure) throw failure;
      deleted.push(uid);
    },
  };
}

const seed = (): Record<string, Record<string, unknown>> => ({
  'users/ana': { displayName: 'Ana' },
  'kitchens/solo': { ownerId: 'ana', memberIds: ['ana'], joinCode: 'solo-code' } satisfies KitchenDoc,
  'kitchens/solo/pantry/rice': { amount: 1 },
  'kitchenInvites/solo-code': { kitchenId: 'solo' },
  'kitchens/shared': {
    ownerId: 'ana',
    memberIds: ['ana', 'bea'],
    memberDisplayNames: { ana: 'Ana', bea: 'Bea' },
    joinCode: 'shared-code',
  } satisfies KitchenDoc,
  'kitchens/shared/pantry/salt': { amount: 1 },
  'kitchenInvites/shared-code': { kitchenId: 'shared' },
  'users/bea': { displayName: 'Bea' },
});

test('deletes the profile, the solo kitchen and the Auth user, and hands the shared kitchen over', async () => {
  const db = fakeDb(seed());
  const auth = fakeAuth();

  const result = await deleteAccount(db as never, auth, { auth: { uid: 'ana' } });

  assert.deepEqual(result, { deletedKitchens: 1, leftKitchens: 1 });
  assert.equal(db.store.has('users/ana'), false);
  assert.equal(db.store.has('kitchens/solo'), false);
  assert.equal(db.store.has('kitchens/solo/pantry/rice'), false);
  assert.equal(db.store.has('kitchenInvites/solo-code'), false);
  assert.deepEqual((db.store.get('kitchens/shared') as KitchenDoc).memberIds, ['bea']);
  assert.equal((db.store.get('kitchens/shared') as KitchenDoc).ownerId, 'bea');
  // The departing name is gone, not only the uid: a deep merge would have kept it.
  assert.deepEqual((db.store.get('kitchens/shared') as KitchenDoc).memberDisplayNames, { bea: 'Bea' });
  assert.equal(db.store.has('kitchens/shared/pantry/salt'), true);
  assert.equal(db.store.has('kitchenInvites/shared-code'), true);
  assert.equal(db.store.has('users/bea'), true);
  assert.deepEqual(auth.deleted, ['ana']);
});

test('asking again after the Auth user is gone still succeeds', async () => {
  const db = fakeDb(seed());

  await deleteAccount(db as never, fakeAuth({ code: 'auth/user-not-found' }), { auth: { uid: 'ana' } });

  assert.equal(db.store.has('users/ana'), false);
});

test('any other Auth failure is reported, so the client can retry', async () => {
  const db = fakeDb(seed());
  await assert.rejects(() => deleteAccount(db as never, fakeAuth({ code: 'auth/internal-error' }), { auth: { uid: 'ana' } }));
});

test('refuses a caller that is not signed in', async () => {
  const db = fakeDb(seed());
  await assert.rejects(() => deleteAccount(db as never, fakeAuth(), {}), /sign in first/);
  assert.equal(db.store.has('users/ana'), true);
});
