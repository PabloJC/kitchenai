// Rules tests. Run them with `npm test` from this directory; see ../README.md.
// Identifiers in the fixtures are opaque on purpose: no ingredient, unit or storage
// location is named anywhere in this repository, fixtures included.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, setDoc } from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));

const ALICE = 'user-alice';
const BOB = 'user-bob';
const NOW_MILLIS = 1700000000000;

let testEnv;
let alice;
let bob;
let anonymous;

before(async () => {
  testEnv = await initializeTestEnvironment({
    // A `demo-` project id is never routed to a real backend by the emulator.
    projectId: 'demo-kitchenai-rules',
    firestore: {
      rules: readFileSync(join(here, '..', 'firestore.rules'), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
  alice = testEnv.authenticatedContext(ALICE).firestore();
  bob = testEnv.authenticatedContext(BOB).firestore();
  anonymous = testEnv.unauthenticatedContext().firestore();
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

const pantryItem = (overrides = {}) => ({
  ingredientId: 'ingredient-1',
  freeText: null,
  amount: 2,
  unitTaxonomy: 'taxonomy-units',
  unitTerm: 'term-1',
  locationTaxonomy: null,
  locationTerm: null,
  expiresAtMillis: null,
  updatedAtMillis: NOW_MILLIS,
  ...overrides,
});

const shoppingList = (overrides = {}) => ({
  labels: { en: 'weekly' },
  updatedAtMillis: NOW_MILLIS,
  ...overrides,
});

const shoppingItem = (overrides = {}) => ({
  ingredientId: 'ingredient-1',
  freeText: null,
  amount: 1,
  unitTaxonomy: null,
  unitTerm: null,
  checked: false,
  sourceRecipeId: null,
  updatedAtMillis: NOW_MILLIS,
  ...overrides,
});

const savedRecipe = (overrides = {}) => ({
  title: 'recipe title',
  summary: null,
  servings: 2,
  ingredients: [{ ingredientId: 'ingredient-1', amount: 1 }],
  steps: ['step one'],
  tags: [],
  source: { type: 'catalogue' },
  updatedAtMillis: NOW_MILLIS,
  ...overrides,
});

const seed = (writer) =>
  testEnv.withSecurityRulesDisabled(async (context) => writer(context.firestore()));

describe('user-owned data', () => {
  it('lets the owner write every declared subcollection', async () => {
    await assertSucceeds(setDoc(doc(alice, `users/${ALICE}/pantry/item-1`), pantryItem()));
    await assertSucceeds(
      setDoc(doc(alice, `users/${ALICE}/shoppingLists/list-1`), shoppingList()),
    );
    await assertSucceeds(
      setDoc(doc(alice, `users/${ALICE}/shoppingLists/list-1/items/item-1`), shoppingItem()),
    );
    await assertSucceeds(
      setDoc(doc(alice, `users/${ALICE}/savedRecipes/recipe-1`), savedRecipe()),
    );
  });

  it('denies another user every read of the same data', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `users/${ALICE}/pantry/item-1`), pantryItem());
      await setDoc(doc(db, `users/${ALICE}/shoppingLists/list-1`), shoppingList());
      await setDoc(doc(db, `users/${ALICE}/shoppingLists/list-1/items/item-1`), shoppingItem());
      await setDoc(doc(db, `users/${ALICE}/savedRecipes/recipe-1`), savedRecipe());
    });

    await assertFails(getDoc(doc(bob, `users/${ALICE}/pantry/item-1`)));
    await assertFails(getDocs(collection(bob, `users/${ALICE}/shoppingLists`)));
    await assertFails(getDoc(doc(bob, `users/${ALICE}/shoppingLists/list-1/items/item-1`)));
    await assertFails(getDoc(doc(bob, `users/${ALICE}/savedRecipes/recipe-1`)));
  });

  it('denies another user every write of the same data', async () => {
    await assertFails(setDoc(doc(bob, `users/${ALICE}/pantry/item-1`), pantryItem()));
    await assertFails(setDoc(doc(bob, `users/${ALICE}/shoppingLists/list-1`), shoppingList()));
    await assertFails(
      setDoc(doc(bob, `users/${ALICE}/shoppingLists/list-1/items/item-1`), shoppingItem()),
    );
    await assertFails(setDoc(doc(bob, `users/${ALICE}/savedRecipes/recipe-1`), savedRecipe()));
  });

  it('denies a document in an undeclared subcollection', async () => {
    await assertFails(setDoc(doc(alice, `users/${ALICE}/invented/doc-1`), { value: 1 }));
    await assertFails(getDoc(doc(alice, `users/${ALICE}/invented/doc-1`)));
  });
});

describe('unauthenticated access', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `users/${ALICE}/pantry/item-1`), pantryItem());
      await setDoc(doc(db, 'taxonomies/taxonomy-1'), { labels: {} });
      await setDoc(doc(db, 'ingredients/ingredient-1'), { labels: {} });
      await setDoc(doc(db, 'recipes/recipe-1'), { title: 'recipe title' });
    });
  });

  it('reads nothing', async () => {
    await assertFails(getDoc(doc(anonymous, `users/${ALICE}`)));
    await assertFails(getDoc(doc(anonymous, `users/${ALICE}/pantry/item-1`)));
    await assertFails(getDoc(doc(anonymous, 'taxonomies/taxonomy-1')));
    await assertFails(getDoc(doc(anonymous, 'ingredients/ingredient-1')));
    await assertFails(getDoc(doc(anonymous, 'recipes/recipe-1')));
  });

  it('writes nothing', async () => {
    await assertFails(setDoc(doc(anonymous, `users/${ALICE}/pantry/item-1`), pantryItem()));
    await assertFails(setDoc(doc(anonymous, 'ingredients/ingredient-1'), { labels: {} }));
  });
});

describe('read-only catalogues', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'taxonomies/taxonomy-1'), { labels: {} });
      await setDoc(doc(db, 'taxonomies/taxonomy-1/terms/term-1'), { labels: {} });
      await setDoc(doc(db, 'ingredients/ingredient-1'), { labels: {} });
      await setDoc(doc(db, 'recipes/recipe-1'), { title: 'recipe title' });
    });
  });

  it('is readable by a signed-in client', async () => {
    await assertSucceeds(getDoc(doc(alice, 'taxonomies/taxonomy-1')));
    await assertSucceeds(getDocs(collection(alice, 'taxonomies/taxonomy-1/terms')));
    await assertSucceeds(getDoc(doc(alice, 'ingredients/ingredient-1')));
    await assertSucceeds(getDoc(doc(alice, 'recipes/recipe-1')));
  });

  it('is writable by nobody', async () => {
    await assertFails(setDoc(doc(alice, 'taxonomies/taxonomy-2'), { labels: {} }));
    await assertFails(setDoc(doc(alice, 'taxonomies/taxonomy-1/terms/term-2'), { labels: {} }));
    await assertFails(setDoc(doc(alice, 'ingredients/ingredient-2'), { labels: {} }));
    await assertFails(setDoc(doc(alice, 'recipes/recipe-2'), { title: 'recipe title' }));
  });
});

describe('pantry item shape', () => {
  const write = (data) => setDoc(doc(alice, `users/${ALICE}/pantry/item-1`), data);

  it('rejects a missing required field', async () => {
    const { amount, ...withoutAmount } = pantryItem();
    await assertFails(write(withoutAmount));

    const { updatedAtMillis, ...withoutTimestamp } = pantryItem();
    await assertFails(write(withoutTimestamp));
  });

  it('rejects an unexpected field', async () => {
    await assertFails(write(pantryItem({ nickname: 'anything' })));
  });

  it('rejects a field of the wrong type or out of range', async () => {
    await assertFails(write(pantryItem({ amount: '2' })));
    await assertFails(write(pantryItem({ amount: 0 })));
    await assertFails(write(pantryItem({ updatedAtMillis: 'now' })));
    await assertFails(write(pantryItem({ ingredientId: '' })));
  });

  it('rejects a holding that is neither an ingredient nor free text, and one that is both', async () => {
    const { ingredientId, ...withoutIngredient } = pantryItem();
    await assertFails(write(withoutIngredient));
    await assertFails(write(pantryItem({ freeText: 'anything' })));
  });

  it('rejects an over-long free-text holding', async () => {
    await assertSucceeds(write(pantryItem({ ingredientId: null, freeText: 'a'.repeat(200) })));
    await assertFails(write(pantryItem({ ingredientId: null, freeText: 'a'.repeat(201) })));
  });
});

describe('shopping item shape', () => {
  const write = (data) =>
    setDoc(doc(alice, `users/${ALICE}/shoppingLists/list-1/items/item-1`), data);

  it('rejects an over-long free-text line', async () => {
    await assertSucceeds(write(shoppingItem({ ingredientId: null, freeText: 'a'.repeat(200) })));
    await assertFails(write(shoppingItem({ ingredientId: null, freeText: 'a'.repeat(201) })));
  });

  it('rejects a line that is neither an ingredient nor free text, and one that is both', async () => {
    await assertFails(write(shoppingItem({ ingredientId: null, freeText: null })));
    await assertFails(write(shoppingItem({ freeText: 'anything' })));
  });

  it('rejects a non-boolean checked flag', async () => {
    await assertFails(write(shoppingItem({ checked: 'false' })));
  });
});

describe('shopping list and saved recipe shape', () => {
  it('rejects an unexpected field on a list', async () => {
    await assertFails(
      setDoc(doc(alice, `users/${ALICE}/shoppingLists/list-1`), shoppingList({ shared: true })),
    );
  });

  it('rejects a saved recipe without a title or with over-long content', async () => {
    const { title, ...untitled } = savedRecipe();
    await assertFails(setDoc(doc(alice, `users/${ALICE}/savedRecipes/recipe-1`), untitled));
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE}/savedRecipes/recipe-1`),
        savedRecipe({ title: 'a'.repeat(201) }),
      ),
    );
    await assertFails(
      setDoc(
        doc(alice, `users/${ALICE}/savedRecipes/recipe-1`),
        savedRecipe({ steps: Array(101).fill('step') }),
      ),
    );
  });
});
