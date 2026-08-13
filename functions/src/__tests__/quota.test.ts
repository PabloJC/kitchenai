import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DAILY_CALL_LIMIT, QuotaExceeded, chargeCall } from '../quota.ts';

/**
 * A store that can also lose a race: two calls arriving together must not both read zero and
 * both decide they are the first, so the fake serialises transactions the way Firestore does.
 */
function fakeDb(seed: Record<string, { calls: number }> = {}) {
  const store = new Map(Object.entries(seed));
  let inFlight = Promise.resolve();
  return {
    writes: store,
    collection: (_name: string) => ({ doc: (id: string) => ({ id }) }),
    runTransaction: <T>(body: (t: unknown) => Promise<T>): Promise<T> => {
      const run = inFlight.then(() =>
        body({
          get: async (ref: { id: string }) => ({ data: () => store.get(ref.id) }),
          set: (ref: { id: string }, value: { calls: number }) => store.set(ref.id, value),
        }),
      );
      inFlight = run.then(
        () => undefined,
        () => undefined,
      );
      return run;
    },
  } as never;
}

const day = new Date('2026-08-13T10:00:00Z');

test('counts the first call of the day', async () => {
  const db = fakeDb();
  assert.equal(await chargeCall(db, 'user-1', day), 1);
});

test('refuses once the day is spent', async () => {
  const db = fakeDb({ [`user-1_2026-08-13`]: { calls: DAILY_CALL_LIMIT } });
  await assert.rejects(() => chargeCall(db, 'user-1', day), QuotaExceeded);
});

test('counts each account separately', async () => {
  const db = fakeDb({ [`user-1_2026-08-13`]: { calls: DAILY_CALL_LIMIT } });
  assert.equal(await chargeCall(db, 'user-2', day), 1);
});

test('a new day is a new allowance', async () => {
  const db = fakeDb({ [`user-1_2026-08-13`]: { calls: DAILY_CALL_LIMIT } });
  assert.equal(await chargeCall(db, 'user-1', new Date('2026-08-14T00:00:01Z')), 1);
});

test('two calls at once do not both count as the first', async () => {
  const db = fakeDb();
  const [a, b] = await Promise.all([chargeCall(db, 'user-1', day), chargeCall(db, 'user-1', day)]);
  assert.deepEqual([a, b].sort(), [1, 2]);
});

test('never lets a burst exceed the limit', async () => {
  const db = fakeDb();
  const attempts = Array.from({ length: DAILY_CALL_LIMIT + 5 }, () => chargeCall(db, 'user-1', day));
  const outcomes = await Promise.allSettled(attempts);
  assert.equal(outcomes.filter((it) => it.status === 'fulfilled').length, DAILY_CALL_LIMIT);
});
