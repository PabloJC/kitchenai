import assert from 'node:assert/strict';
import { test } from 'node:test';
import { HttpsError } from 'firebase-functions/v2/https';
import { REQUESTS, freeTextKey, requestId } from '../ingredientRequests.ts';
import { migrateFreeText } from '../migrateFreeText.ts';

const DELETE = Symbol('delete');
const NOW = 5000;

/** Documents by path, with the reads, collection-group query and batch the migration uses. */
function fakeDb(seed: Record<string, Record<string, unknown>>) {
  const store = new Map(Object.entries(seed).map(([path, data]) => [path, { ...data }]));
  const ref = (path: string) => ({ path });
  const snapshot = (path: string) => ({ exists: store.has(path), ref: ref(path), get: (f: string) => store.get(path)?.[f] });
  const update = (path: string, change: Record<string, unknown>) => {
    const next: Record<string, unknown> = { ...store.get(path), ...change };
    for (const [field, value] of Object.entries(change)) if (value === DELETE) delete next[field];
    store.set(path, next);
  };
  let writes = 0;
  const db = {
    store,
    writes: () => writes,
    collection: (name: string) => ({
      doc: (id: string) => ({
        path: `${name}/${id}`,
        get: async () => snapshot(`${name}/${id}`),
        update: async (change: Record<string, unknown>) => {
          writes += 1;
          update(`${name}/${id}`, change);
        },
      }),
    }),
    collectionGroup: (group: string) => ({
      where: (field: string, op: string, value: string) => {
        assert.deepEqual([field, op, value], ['freeText', '>=', '']);
        return {
          get: async () => ({
            docs: [...store.keys()]
              .filter((path) => path.split('/').at(-2) === group && typeof store.get(path)?.freeText === 'string')
              .map(snapshot),
          }),
        };
      },
    }),
    batch: () => {
      const pending: [string, Record<string, unknown>][] = [];
      return {
        update: (target: { path: string }, change: Record<string, unknown>) => pending.push([target.path, change]),
        commit: async () => {
          writes += pending.length;
          for (const [path, change] of pending) update(path, change);
        },
      };
    },
  };
  return db as typeof db & never;
}

const id = requestId(freeTextKey('Tomate cherry')!);
const admin = { token: { admin: true } };
const deps = { deleteField: DELETE, nowMillis: NOW };

function kitchen(request: Record<string, unknown> = { status: 'promoted', ingredientId: 'cherry-tomato' }) {
  return fakeDb({
    [`${REQUESTS}/${id}`]: { text: 'Tomate cherry', count: 3, activeCount: 3, ...request },
    'ingredients/cherry-tomato': { labels: { es: 'Tomate cherry' } },
    'ingredients/old': { labels: { en: 'Old' }, archived: true },
    'kitchens/k1/pantry/a': { freeText: 'Tomate cherry', amount: 2, locationTerm: 'fridge', updatedAtMillis: 1 },
    'kitchens/k2/pantry/b': { freeText: 'tomate  CHERRY', amount: 1, updatedAtMillis: 1 },
    'kitchens/k1/pantry/c': { freeText: 'Tomate', amount: 1, updatedAtMillis: 1 },
    'kitchens/k1/pantry/d': { ingredientId: 'egg', amount: 6, updatedAtMillis: 1 },
    'kitchens/k1/shoppingLists/l/items/e': { freeText: 'Tomate cherry', checked: false, updatedAtMillis: 1 },
    // Same group name, other place: not a shopping item, never touched.
    'elsewhere/x/items/f': { freeText: 'Tomate cherry' },
  });
}

async function refusal(db: ReturnType<typeof fakeDb>, call: Parameters<typeof migrateFreeText>[1]) {
  try {
    await migrateFreeText(db, call, deps);
  } catch (failure) {
    assert.ok(failure instanceof HttpsError);
    assert.equal(db.writes(), 0, 'nothing may be written on a refusal');
    return failure.code;
  }
  assert.fail('expected a refusal');
}

test('converts every spelling the request counted, keeping the rest of each item', async () => {
  const db = kitchen();
  const result = await migrateFreeText(db, { data: { requestId: id }, auth: admin }, deps);

  assert.deepEqual(result, { pantryItems: 2, shoppingItems: 1, dryRun: false });
  assert.deepEqual(db.store.get('kitchens/k1/pantry/a'), {
    ingredientId: 'cherry-tomato',
    amount: 2,
    locationTerm: 'fridge',
    updatedAtMillis: NOW,
  });
  assert.equal(db.store.get('kitchens/k2/pantry/b')?.ingredientId, 'cherry-tomato');
  assert.deepEqual(db.store.get('kitchens/k1/shoppingLists/l/items/e'), {
    ingredientId: 'cherry-tomato',
    checked: false,
    updatedAtMillis: NOW,
  });
  assert.equal(db.store.get('kitchens/k1/pantry/c')?.freeText, 'Tomate');
  assert.equal(db.store.get('elsewhere/x/items/f')?.freeText, 'Tomate cherry');
  assert.deepEqual(db.store.get(`${REQUESTS}/${id}`), {
    text: 'Tomate cherry',
    count: 3,
    activeCount: 0,
    status: 'promoted',
    ingredientId: 'cherry-tomato',
    migratedAtMillis: NOW,
  });
});

test('a dry run returns the same counts and writes nothing', async () => {
  const db = kitchen();
  const result = await migrateFreeText(db, { data: { requestId: id, dryRun: true }, auth: admin }, deps);

  assert.deepEqual(result, { pantryItems: 2, shoppingItems: 1, dryRun: true });
  assert.equal(db.writes(), 0);
});

test('a second run changes nothing', async () => {
  const db = kitchen();
  await migrateFreeText(db, { data: { requestId: id }, auth: admin }, deps);
  const before = structuredClone(Object.fromEntries(db.store));

  const again = await migrateFreeText(db, { data: { requestId: id }, auth: admin }, { ...deps, nowMillis: NOW + 1 });

  assert.deepEqual(again, { pantryItems: 0, shoppingItems: 0, dryRun: false });
  const after = Object.fromEntries(db.store);
  delete (after[`${REQUESTS}/${id}`] as Record<string, unknown>).migratedAtMillis;
  delete (before[`${REQUESTS}/${id}`] as Record<string, unknown>).migratedAtMillis;
  assert.deepEqual(after, before);
});

test('only an admin may call it, and the claim is checked before the payload', async () => {
  assert.equal(await refusal(kitchen(), { data: { requestId: id } }), 'unauthenticated');
  assert.equal(await refusal(kitchen(), { data: { requestId: id }, auth: { token: {} } }), 'permission-denied');
  assert.equal(await refusal(kitchen(), { data: 'garbage', auth: { token: { admin: 'true' } } }), 'permission-denied');
});

test('refuses a malformed payload', async () => {
  for (const data of [null, { requestId: 'Tomate cherry' }, { requestId: id, dryRun: 'yes' }, { requestId: id, kitchen: 'k1' }]) {
    assert.equal(await refusal(kitchen(), { data, auth: admin }), 'invalid-argument');
  }
});

test('refuses an open request, a missing one and an archived or missing target', async () => {
  assert.equal(await refusal(kitchen({ status: 'open' }), { data: { requestId: id }, auth: admin }), 'failed-precondition');
  assert.equal(await refusal(kitchen({ status: 'dismissed' }), { data: { requestId: id }, auth: admin }), 'failed-precondition');
  assert.equal(
    await refusal(kitchen({ status: 'aliased', ingredientId: 'old' }), { data: { requestId: id }, auth: admin }),
    'failed-precondition',
  );
  assert.equal(
    await refusal(kitchen({ status: 'aliased', ingredientId: 'gone' }), { data: { requestId: id }, auth: admin }),
    'failed-precondition',
  );
  const other = requestId('kombu');
  assert.equal(await refusal(kitchen(), { data: { requestId: other }, auth: admin }), 'failed-precondition');
});
