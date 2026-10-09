import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { unknownPurposes } from '../lib/seed-checks.mjs';

const taxonomies = JSON.parse(readFileSync(new URL('../../firebase/seed/taxonomies.json', import.meta.url), 'utf8'));

test('the shipped seed declares only purposes the client knows', () => {
  assert.deepEqual(unknownPurposes(taxonomies), []);
});

test('dish types are declared as describing recipes, which keeps them off the profile', () => {
  assert.equal(taxonomies['dish-types'].purpose, 'RECIPE_CLASSIFICATION');
});

test('a purpose the client has no name for is reported by taxonomy id', () => {
  const typo = { ...taxonomies, cuisines: { ...taxonomies.cuisines, purpose: 'RECIPE_CLASIFICATION' } };
  assert.deepEqual(unknownPurposes(typo), ['cuisines']);
});

test('a taxonomy with no purpose is fine', () => {
  assert.deepEqual(unknownPurposes({ diets: { labels: {} } }), []);
});
