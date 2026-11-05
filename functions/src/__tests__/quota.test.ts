import assert from 'node:assert/strict';
import { test } from 'node:test';
import { IMAGES, IMAGE_DAILY_LIMIT, QuotaExceeded, chargeCall, mealSlot, withMealSlot } from '../quota.ts';

type Doc = Record<string, unknown>;

/**
 * A store that can also lose a race: two calls arriving together must not both read zero and
 * both decide they are the first, so the fake serialises transactions the way Firestore does.
 */
function fakeDb(seed: Record<string, Doc> = {}) {
  const store = new Map(Object.entries(seed));
  let inFlight = Promise.resolve();
  const ref = (id: string) => ({
    id,
    set: async (value: Doc) => void store.set(id, value),
    delete: async () => void store.delete(id),
  });
  return {
    writes: store,
    collection: (_name: string) => ({ doc: ref }),
    runTransaction: <T>(body: (t: unknown) => Promise<T>): Promise<T> => {
      const run = inFlight.then(() =>
        body({
          get: async (doc: { id: string }) => ({ data: () => store.get(doc.id) }),
          set: (doc: { id: string }, value: Doc) => store.set(doc.id, value),
        }),
      );
      inFlight = run.then(
        () => undefined,
        () => undefined,
      );
      return run;
    },
  };
}

const day = new Date('2026-08-13T10:00:00Z');
const dinner = 'user-1_2026-08-13_meal_dinner';
const ideas = async () => ['idea'];

test('counts the first image of the day', async () => {
  const db = fakeDb();
  assert.equal(await chargeCall(db as never, 'user-1', day, IMAGES), 1);
});

test('refuses images once their allowance is spent', async () => {
  const db = fakeDb({ [`user-1_2026-08-13_images`]: { calls: IMAGE_DAILY_LIMIT } });
  await assert.rejects(() => chargeCall(db as never, 'user-1', day, IMAGES), QuotaExceeded);
});

test('allows one photo for each of five sets of five ideas', () => {
  assert.equal(IMAGE_DAILY_LIMIT, 25);
});

test('never lets a burst of images exceed the limit', async () => {
  const db = fakeDb();
  const attempts = Array.from({ length: IMAGE_DAILY_LIMIT + 5 }, () => chargeCall(db as never, 'user-1', day, IMAGES));
  const outcomes = await Promise.allSettled(attempts);
  assert.equal(outcomes.filter((it) => it.status === 'fulfilled').length, IMAGE_DAILY_LIMIT);
});

test('names a slot after the meal, and a run with none after any', () => {
  assert.equal(mealSlot('BREAKFAST'), 'breakfast');
  assert.equal(mealSlot('SNACK'), 'snack');
  assert.equal(mealSlot(null), 'any');
});

test('keeps the slot once a run returns ideas', async () => {
  const db = fakeDb();
  assert.deepEqual(await withMealSlot(db as never, 'user-1', day, 'dinner', ideas), ['idea']);
  assert.equal(db.writes.get(dinner)?.used, true);
});

test('refuses a second run for the same meal without running it', async () => {
  const db = fakeDb({ [dinner]: { used: true } });
  let ran = false;
  const run = async () => {
    ran = true;
    return ['idea'];
  };
  await assert.rejects(() => withMealSlot(db as never, 'user-1', day, 'dinner', run), QuotaExceeded);
  assert.equal(ran, false);
});

test('each meal, and any, is a slot of its own', async () => {
  const db = fakeDb({ [dinner]: { used: true } });
  for (const slot of ['breakfast', 'lunch', 'snack', 'any'] as const) {
    assert.deepEqual(await withMealSlot(db as never, 'user-1', day, slot, ideas), ['idea']);
  }
});

test('each account has its own slots', async () => {
  const db = fakeDb({ [dinner]: { used: true } });
  assert.deepEqual(await withMealSlot(db as never, 'user-2', day, 'dinner', ideas), ['idea']);
});

test('a new day gives the meal back', async () => {
  const db = fakeDb({ [dinner]: { used: true } });
  const tomorrow = new Date('2026-08-14T00:00:01Z');
  assert.deepEqual(await withMealSlot(db as never, 'user-1', tomorrow, 'dinner', ideas), ['idea']);
});

test('a failed run gives the slot back and still fails', async () => {
  const db = fakeDb();
  const failing = async (): Promise<string[]> => {
    throw new Error('model down');
  };
  await assert.rejects(() => withMealSlot(db as never, 'user-1', day, 'dinner', failing), /model down/);
  assert.equal(db.writes.has(dinner), false);
  assert.deepEqual(await withMealSlot(db as never, 'user-1', day, 'dinner', ideas), ['idea']);
});

test('a run that finds nothing gives the slot back', async () => {
  const db = fakeDb();
  assert.deepEqual(await withMealSlot(db as never, 'user-1', day, 'dinner', async () => []), []);
  assert.equal(db.writes.has(dinner), false);
});

test('two runs at once for the same meal make one call', async () => {
  const db = fakeDb();
  let calls = 0;
  const slow = async () => {
    calls += 1;
    await new Promise((resolve) => setTimeout(resolve, 10));
    return ['idea'];
  };
  const outcomes = await Promise.allSettled([
    withMealSlot(db as never, 'user-1', day, 'dinner', slow),
    withMealSlot(db as never, 'user-1', day, 'dinner', slow),
  ]);
  assert.equal(calls, 1);
  assert.deepEqual(outcomes.map((it) => it.status).sort(), ['fulfilled', 'rejected']);
});

test('a claim left by a run that died frees the slot once it expires', async () => {
  const db = fakeDb({ [dinner]: { claimedUntil: day.getTime() - 1 } });
  assert.deepEqual(await withMealSlot(db as never, 'user-1', day, 'dinner', ideas), ['idea']);
});

test('a live claim refuses another run', async () => {
  const db = fakeDb({ [dinner]: { claimedUntil: day.getTime() + 60_000 } });
  await assert.rejects(() => withMealSlot(db as never, 'user-1', day, 'dinner', ideas), QuotaExceeded);
});
