import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  REQUESTS,
  displayText,
  freeTextChange,
  freeTextKey,
  recordFreeTextChange,
  requestId,
} from '../ingredientRequests.ts';

/** Documents by path, with the slice of the transaction API the queue uses. */
function fakeDb(seed: Record<string, Record<string, unknown>> = {}) {
  const store = new Map(Object.entries(seed));
  const snapshot = (path: string) => ({ exists: store.has(path), get: (field: string) => store.get(path)?.[field] });
  const db = {
    store,
    collection: (name: string) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    runTransaction: async <T>(body: (t: unknown) => Promise<T>): Promise<T> =>
      body({
        get: async (ref: { path: string }) => snapshot(ref.path),
        set: (ref: { path: string }, value: Record<string, unknown>) => store.set(ref.path, { ...value }),
        update: (ref: { path: string }, value: Record<string, unknown>) =>
          store.set(ref.path, { ...store.get(ref.path), ...value }),
      }),
  };
  return db as typeof db & never;
}

const pathOf = (text: string) => `${REQUESTS}/${requestId(freeTextKey(text)!)}`;

async function write(db: ReturnType<typeof fakeDb>, before: unknown, after: unknown, now = 1000) {
  const change = freeTextChange(before, after);
  if (change !== null) await recordFreeTextChange(db, change, now);
}

test('case, accents and spacing do not make a different request', () => {
  assert.equal(freeTextKey('  Tomate   cherry '), 'tomate cherry');
  assert.equal(freeTextKey('Jalapeño'), freeTextKey('jalapeno'));
  assert.equal(displayText('  Tomate   cherry '), 'Tomate cherry');
  assert.equal(freeTextKey('   '), null);
  assert.equal(freeTextKey(42), null);
});

test('the document id is a fixed-length hash, never the text', () => {
  const id = requestId(freeTextKey('Tomate cherry')!);
  assert.match(id, /^[0-9a-f]{32}$/);
  assert.equal(id, requestId('tomate cherry'));
});

test('two kitchens typing the same thing differently give one request counted twice', async () => {
  const db = fakeDb();
  await write(db, undefined, { freeText: 'Tomate cherry', amount: 3, kitchenId: 'kitchen-1' }, 1000);
  await write(db, undefined, { freeText: 'tomate  cherry' }, 2000);

  assert.equal(db.store.size, 1);
  assert.deepEqual(db.store.get(pathOf('tomate cherry')), {
    text: 'Tomate cherry',
    count: 2,
    activeCount: 2,
    firstSeenMillis: 1000,
    lastSeenMillis: 2000,
    status: 'open',
  });
});

test('a request holds only the text and counts, whatever the item carried', async () => {
  const db = fakeDb();
  await write(db, undefined, { freeText: 'Kale', amount: 2, unitTerm: 'piece', createdBy: 'user-1', location: 'fridge' });

  assert.deepEqual(Object.keys(db.store.get(pathOf('kale'))!).sort(), [
    'activeCount',
    'count',
    'firstSeenMillis',
    'lastSeenMillis',
    'status',
    'text',
  ]);
});

test('deleting an item lowers how many carry the text, not how often it was typed', async () => {
  const db = fakeDb();
  await write(db, undefined, { freeText: 'Kale' });
  await write(db, { freeText: 'Kale' }, undefined);

  assert.equal(db.store.get(pathOf('kale'))!.count, 1);
  assert.equal(db.store.get(pathOf('kale'))!.activeCount, 0);
});

test('renaming moves one active item from the old text to the new one', async () => {
  const db = fakeDb();
  await write(db, undefined, { freeText: 'Kale' });
  await write(db, { freeText: 'Kale' }, { freeText: 'Curly kale' });

  assert.equal(db.store.get(pathOf('kale'))!.activeCount, 0);
  assert.equal(db.store.get(pathOf('curly kale'))!.activeCount, 1);
});

test('an edit that keeps the same text, or an item without free text, writes nothing', () => {
  assert.equal(freeTextChange({ freeText: 'Kale', amount: 1 }, { freeText: ' kale ', amount: 2 }), null);
  assert.equal(freeTextChange(undefined, { ingredientId: 'rice' }), null);
  assert.equal(freeTextChange({ ingredientId: 'rice' }, undefined), null);
});

test('an item picked from the catalogue after being typed is a removal only', () => {
  assert.deepEqual(freeTextChange({ freeText: 'Kale' }, { ingredientId: 'kale' }), { added: null, removed: 'Kale' });
});

test('the trigger never reopens a closed request or touches its ingredient', async () => {
  const db = fakeDb({
    [pathOf('kale')]: {
      text: 'Kale',
      count: 4,
      activeCount: 0,
      firstSeenMillis: 1,
      lastSeenMillis: 2,
      status: 'promoted',
      ingredientId: 'kale',
    },
  });
  await write(db, undefined, { freeText: 'KALE' }, 3);

  assert.deepEqual(db.store.get(pathOf('kale')), {
    text: 'Kale',
    count: 5,
    activeCount: 1,
    firstSeenMillis: 1,
    lastSeenMillis: 3,
    status: 'promoted',
    ingredientId: 'kale',
  });
});

test('the active count never goes below zero', async () => {
  const db = fakeDb();
  await write(db, { freeText: 'Kale' }, undefined);

  assert.equal(db.store.size, 0);
});
