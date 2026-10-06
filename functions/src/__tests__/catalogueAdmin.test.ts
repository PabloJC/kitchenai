import assert from 'node:assert/strict';
import { test } from 'node:test';
import { HttpsError } from 'firebase-functions/v2/https';
import { isAdmin, writeCatalogueEntry } from '../catalogueAdmin.ts';

/** Records every document path it is asked to write, and serialises nothing else. */
function fakeDb(seed: Record<string, unknown> = {}) {
  const store = new Map(Object.entries(seed));
  const db = {
    store,
    collection: (name: string) => ({ doc: (id: string) => ({ path: `${name}/${id}` }) }),
    runTransaction: async <T>(body: (t: unknown) => Promise<T>): Promise<T> =>
      body({
        get: async (ref: { path: string }) => ({ exists: store.has(ref.path) }),
        set: (ref: { path: string }, value: unknown) => store.set(ref.path, value),
      }),
  };
  return db as typeof db & never;
}

const admin = { token: { admin: true } };
const ingredient = { kind: 'ingredient', id: 'brown-rice', data: { labels: { en: 'Brown rice' }, tags: {} } };
const recipe = {
  kind: 'recipe',
  id: 'rice-bowl',
  data: { title: 'Rice bowl', servings: 1, ingredients: [{ ingredientId: 'brown-rice' }], steps: ['Cook the rice.'] },
};

async function code(call: Parameters<typeof writeCatalogueEntry>[1], db = fakeDb()) {
  try {
    await writeCatalogueEntry(db, call);
  } catch (failure) {
    assert.ok(failure instanceof HttpsError);
    assert.equal(db.store.size, 0, 'nothing may be written on a refusal');
    return failure.code;
  }
  assert.fail('expected a refusal');
}

test('refuses an unauthenticated caller', async () => {
  assert.equal(await code({ data: ingredient }), 'unauthenticated');
});

test('refuses a signed-in caller without the claim', async () => {
  assert.equal(await code({ data: ingredient, auth: { token: {} } }), 'permission-denied');
});

test('only the boolean true passes as admin', async () => {
  for (const value of [false, 'true', 1, null, {}]) {
    assert.equal(isAdmin({ token: { admin: value } }), false);
    assert.equal(await code({ data: ingredient, auth: { token: { admin: value } } }), 'permission-denied');
  }
  assert.equal(isAdmin(admin), true);
});

test('checks the claim before looking at the payload', async () => {
  assert.equal(await code({ data: 'garbage', auth: { token: {} } }), 'permission-denied');
});

test('refuses an invalid payload from an admin', async () => {
  assert.equal(await code({ data: { ...ingredient, id: 'a/b' }, auth: admin }), 'invalid-argument');
  assert.equal(await code({ data: { ...ingredient, data: { labels: {} } }, auth: admin }), 'invalid-argument');
  assert.equal(await code({ data: null, auth: admin }), 'invalid-argument');
});

test('an admin writes an ingredient under ingredients/{id}', async () => {
  const db = fakeDb();
  const result = await writeCatalogueEntry(db, { data: ingredient, auth: admin });
  assert.deepEqual(result, { kind: 'ingredient', id: 'brown-rice', created: true });
  assert.deepEqual([...db.store.keys()], ['ingredients/brown-rice']);
  assert.deepEqual(db.store.get('ingredients/brown-rice'), { labels: { en: 'Brown rice' }, tags: {} });
});

test('an admin writes a recipe under recipes/{id} with catalogue provenance', async () => {
  const db = fakeDb();
  const result = await writeCatalogueEntry(db, { data: recipe, auth: admin });
  assert.deepEqual(result, { kind: 'recipe', id: 'rice-bowl', created: true });
  assert.deepEqual([...db.store.keys()], ['recipes/rice-bowl']);
  assert.deepEqual((db.store.get('recipes/rice-bowl') as { source: unknown }).source, { type: 'catalogue' });
});

test('an existing id is replaced by the new payload, like the seed script', async () => {
  const db = fakeDb({ 'ingredients/brown-rice': { labels: { en: 'Old' }, tags: { allergens: ['x'] }, stale: true } });
  const result = await writeCatalogueEntry(db, { data: ingredient, auth: admin });
  assert.equal(result.created, false);
  assert.deepEqual(db.store.get('ingredients/brown-rice'), { labels: { en: 'Brown rice' }, tags: {} });
});

test('the same id in the other collection is a different document', async () => {
  const db = fakeDb({ 'recipes/brown-rice': { title: 'Untouched' } });
  await writeCatalogueEntry(db, { data: ingredient, auth: admin });
  assert.deepEqual(db.store.get('recipes/brown-rice'), { title: 'Untouched' });
  assert.ok(db.store.has('ingredients/brown-rice'));
});
