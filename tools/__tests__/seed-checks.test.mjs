import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { invalidPluralLabels, unknownPurposes } from '../lib/seed-checks.mjs';

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

const withTerm = (term) => ({ units: { terms: { piece: { labels: { en: 'piece', es: 'unidad' }, ...term } } } });

test('the shipped seed has no malformed plural labels', () => {
  assert.deepEqual(invalidPluralLabels(taxonomies), []);
});

test('the countable unit carries a plural in every language it has a label for', () => {
  const { labels, pluralLabels } = taxonomies.units.terms.piece;
  assert.deepEqual(Object.keys(pluralLabels).sort(), Object.keys(labels).sort());
});

test('a term without plural labels is fine', () => {
  assert.deepEqual(invalidPluralLabels(withTerm({})), []);
});

test('plural labels in the languages of the term are fine', () => {
  assert.deepEqual(invalidPluralLabels(withTerm({ pluralLabels: { es: 'unidades' } })), []);
});

test('a plural in a language the term has no label for is reported by taxonomy and term', () => {
  assert.deepEqual(invalidPluralLabels(withTerm({ pluralLabels: { fr: 'pieces' } })), ['units/piece']);
});

test('a blank or non-string plural is reported', () => {
  assert.deepEqual(invalidPluralLabels(withTerm({ pluralLabels: { es: ' ' } })), ['units/piece']);
  assert.deepEqual(invalidPluralLabels(withTerm({ pluralLabels: { es: 3 } })), ['units/piece']);
  assert.deepEqual(invalidPluralLabels(withTerm({ pluralLabels: 'unidades' })), ['units/piece']);
});
