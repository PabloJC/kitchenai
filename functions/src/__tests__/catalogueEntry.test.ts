import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { parseCatalogueEntry } from '../catalogueEntry.ts';
import { BadRequest } from '../contract.ts';

const ingredient = {
  labels: { en: 'Egg', es: 'Huevo' },
  defaultUnitTaxonomy: 'units',
  defaultUnitTerm: 'piece',
  purchasedWhole: true,
  tags: { 'food-groups': ['pantry-staple'], allergens: ['egg'] },
};

const recipe = {
  title: 'Tomato pasta',
  summary: 'Quick weeknight dinner.',
  servings: 2,
  totalMinutes: 20,
  ingredients: [
    { ingredientId: 'pasta', amount: 200, unitTaxonomy: 'units', unitTerm: 'gram' },
    { freeText: 'a pinch of salt', optional: true },
  ],
  steps: ['Boil the pasta.', 'Add the sauce.'],
  tags: [{ taxonomy: 'dish-types', term: 'pasta' }],
};

const ingredientEntry = (data: unknown, id: unknown = 'egg') => ({ kind: 'ingredient', id, data });
const recipeEntry = (data: unknown, id: unknown = 'tomato-pasta') => ({ kind: 'recipe', id, data });

function refuses(raw: unknown) {
  assert.throws(() => parseCatalogueEntry(raw), BadRequest);
}

test('every document in the seed file is a valid ingredient payload', () => {
  const seed = JSON.parse(
    readFileSync(new URL('../../../firebase/seed/ingredients.json', import.meta.url), 'utf8'),
  ) as Record<string, unknown>;
  for (const [id, data] of Object.entries(seed)) {
    assert.deepEqual(parseCatalogueEntry(ingredientEntry(data, id)).document, data, id);
  }
});

test('accepts an ingredient and keeps exactly its fields', () => {
  const entry = parseCatalogueEntry(ingredientEntry(ingredient));
  assert.equal(entry.kind, 'ingredient');
  assert.equal(entry.id, 'egg');
  assert.deepEqual(entry.document, ingredient);
});

test('an ingredient needs only labels', () => {
  const entry = parseCatalogueEntry(ingredientEntry({ labels: { en: 'Salt' } }));
  assert.deepEqual(entry.document, { labels: { en: 'Salt' }, tags: {} });
});

test('refuses an ingredient with an unknown field', () => {
  refuses(ingredientEntry({ ...ingredient, calories: 70 }));
});

test('refuses empty, blank or missing labels', () => {
  refuses(ingredientEntry({ ...ingredient, labels: {} }));
  refuses(ingredientEntry({ ...ingredient, labels: { en: '  ' } }));
  refuses(ingredientEntry({ tags: {} }));
  refuses(ingredientEntry({ ...ingredient, labels: { 'not a tag': 'Egg' } }));
});

test('refuses half a default unit', () => {
  refuses(ingredientEntry({ ...ingredient, defaultUnitTerm: undefined }));
  refuses(ingredientEntry({ ...ingredient, defaultUnitTaxonomy: undefined }));
});

test('refuses tags that are not a map of identifier lists', () => {
  refuses(ingredientEntry({ ...ingredient, tags: ['allergens'] }));
  refuses(ingredientEntry({ ...ingredient, tags: { allergens: 'egg' } }));
  refuses(ingredientEntry({ ...ingredient, tags: { allergens: ['has spaces'] } }));
});

test('refuses ids that are not plain identifiers', () => {
  for (const id of ['', ' ', 'a/b', 'has space', '.', '..', '__reserved__', 'x'.repeat(129), 42, null]) {
    refuses(ingredientEntry(ingredient, id));
  }
});

test('refuses an unknown kind, including a collection name', () => {
  refuses({ kind: 'taxonomy', id: 'diets', data: ingredient });
  refuses({ kind: 'users', id: 'someone', data: ingredient });
  refuses({ id: 'egg', data: ingredient });
});

test('refuses extra top level fields such as a collection path', () => {
  refuses({ ...ingredientEntry(ingredient), collection: 'users' });
});

test('refuses a payload that is not an object', () => {
  for (const raw of [null, undefined, 'egg', 3, [], { kind: 'ingredient', id: 'egg' }]) refuses(raw);
});

test('accepts a recipe and stamps catalogue provenance itself', () => {
  const entry = parseCatalogueEntry(recipeEntry(recipe));
  assert.equal(entry.kind, 'recipe');
  assert.deepEqual(entry.document, { ...recipe, source: { type: 'catalogue' } });
});

test('a recipe cannot claim its own provenance or save time', () => {
  refuses(recipeEntry({ ...recipe, source: { type: 'agent' } }));
  refuses(recipeEntry({ ...recipe, savedAtMillis: 1 }));
});

test('refuses a recipe with an unknown field', () => {
  refuses(recipeEntry({ ...recipe, difficulty: 'easy' }));
});

test('refuses a recipe missing what it cannot be cooked without', () => {
  refuses(recipeEntry({ ...recipe, title: '' }));
  refuses(recipeEntry({ ...recipe, title: undefined }));
  refuses(recipeEntry({ ...recipe, servings: undefined }));
  refuses(recipeEntry({ ...recipe, ingredients: [] }));
  refuses(recipeEntry({ ...recipe, steps: [] }));
  refuses(recipeEntry({ ...recipe, steps: ['Boil.', ''] }));
});

test('refuses servings and minutes that are not whole and in range', () => {
  refuses(recipeEntry({ ...recipe, servings: 0 }));
  refuses(recipeEntry({ ...recipe, servings: 2.5 }));
  refuses(recipeEntry({ ...recipe, servings: '2' }));
  refuses(recipeEntry({ ...recipe, totalMinutes: -5 }));
});

test('a recipe line is either a catalogue ingredient or free text, never both or neither', () => {
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', freeText: 'pasta' }] }));
  refuses(recipeEntry({ ...recipe, ingredients: [{ amount: 1 }] }));
});

test('refuses a recipe line with a bad amount or a unit without one', () => {
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', amount: 0 }] }));
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', amount: '1' }] }));
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', unitTaxonomy: 'units', unitTerm: 'gram' }] }));
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', amount: 1, unitTerm: 'gram' }] }));
});

test('refuses a recipe line with an unknown field', () => {
  refuses(recipeEntry({ ...recipe, ingredients: [{ ingredientId: 'pasta', brand: 'x' }] }));
});

test('refuses recipe tags that are not term references', () => {
  refuses(recipeEntry({ ...recipe, tags: { 'dish-types': ['pasta'] } }));
  refuses(recipeEntry({ ...recipe, tags: [{ taxonomy: 'dish-types' }] }));
  refuses(recipeEntry({ ...recipe, tags: [{ taxonomy: 'dish-types', term: 'pasta', extra: 1 }] }));
});
