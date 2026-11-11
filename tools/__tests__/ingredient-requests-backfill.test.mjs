import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  UsageError,
  isFreeTextItemPath,
  parseBackfillArgs,
  planBackfill,
} from '../lib/ingredient-requests-backfill.mjs';
import { freeTextKey, requestId } from '../../functions/src/ingredientRequests.ts';

const idOf = (text) => requestId(freeTextKey(text));
const pantry = (n, freeText, updatedAtMillis = n) => ({ path: `kitchens/k${n}/pantry/i${n}`, freeText, updatedAtMillis });

test('only pantry and shopping items are scanned', () => {
  assert.equal(isFreeTextItemPath('kitchens/k1/pantry/i1'), true);
  assert.equal(isFreeTextItemPath('kitchens/k1/shoppingLists/l1/items/i1'), true);
  assert.equal(isFreeTextItemPath('kitchens/k1/savedRecipes/r1'), false);
  assert.equal(isFreeTextItemPath('other/x/items/i1'), false);
});

test('items of every kitchen fold into one request per text, with no identifier in it', () => {
  const writes = planBackfill([pantry(1, 'Tomate cherry', 10), pantry(2, 'tomate  cherry', 20), pantry(3, null)], new Map(), 99);

  assert.deepEqual([...writes.keys()], [idOf('tomate cherry')]);
  assert.deepEqual(writes.get(idOf('tomate cherry')), {
    text: 'Tomate cherry',
    count: 2,
    activeCount: 2,
    firstSeenMillis: 10,
    lastSeenMillis: 20,
    status: 'open',
  });
});

test('an existing request keeps its status and history; only the active count is recounted', () => {
  const existing = new Map([
    [idOf('kale'), { text: 'Kale', count: 9, activeCount: 4, firstSeenMillis: 1, lastSeenMillis: 2, status: 'promoted', ingredientId: 'kale' }],
    [idOf('chard'), { text: 'Chard', count: 1, activeCount: 1, firstSeenMillis: 1, lastSeenMillis: 1, status: 'open' }],
  ]);
  const writes = planBackfill([pantry(1, 'kale')], existing, 99);

  assert.deepEqual(writes.get(idOf('kale')), { ...existing.get(idOf('kale')), activeCount: 1 });
  assert.equal(writes.get(idOf('chard')).activeCount, 0);
});

test('a second run over the same items writes nothing', () => {
  const items = [pantry(1, 'Kale'), pantry(2, 'Chard')];
  const first = planBackfill(items, new Map(), 99);
  const second = planBackfill(items, first, 99);

  assert.equal(first.size, 2);
  assert.equal(second.size, 0);
});

test('the arguments default to a dry run and need a project', () => {
  assert.deepEqual(parseBackfillArgs(['--project', 'p']), { project: 'p', write: false, help: false });
  assert.equal(parseBackfillArgs(['--project', 'p', '--write']).write, true);
  assert.throws(() => parseBackfillArgs([]), UsageError);
  assert.throws(() => parseBackfillArgs(['--project', 'p', '--force']), UsageError);
});
