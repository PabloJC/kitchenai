// Rules tests. Run them with `npm test` from this directory; see ../README.md.
// Identifiers in the fixtures are opaque on purpose: no ingredient, unit or storage
// location is named anywhere in this repository, fixtures included.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, runTransaction, setDoc, updateDoc, writeBatch } from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));

const ALICE = 'user-alice';
const BOB = 'user-bob';
const CAROL = 'user-carol';
const NOW_MILLIS = 1700000000000;

// Alice owns kitchen-1 and shares it with Bob; kitchen-2 is Bob's alone, so it is what proves a
// non-member of one kitchen cannot reach another's data.
const KITCHEN_1 = 'kitchen-1';
const KITCHEN_2 = 'kitchen-2';
const JOIN_CODE_1 = 'join-code-1';
const JOIN_CODE_2 = 'join-code-2';

let testEnv;
let alice;
let bob;
let carol;
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
  carol = testEnv.authenticatedContext(CAROL).firestore();
  anonymous = testEnv.unauthenticatedContext().firestore();
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

const kitchen = (overrides = {}) => ({
  ownerId: ALICE,
  memberIds: [ALICE, BOB],
  joinCode: JOIN_CODE_1,
  memberDisplayNames: {},
  ...overrides,
});

const invite = (kitchenId) => ({ kitchenId });

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

// Alice owns kitchen-1 with Bob as a member; Bob alone owns kitchen-2, with its own invite.
const seedKitchens = () =>
  seed(async (db) => {
    await setDoc(doc(db, `kitchens/${KITCHEN_1}`), kitchen());
    await setDoc(doc(db, `kitchenInvites/${JOIN_CODE_1}`), invite(KITCHEN_1));
    await setDoc(doc(db, `kitchens/${KITCHEN_2}`), kitchen({ ownerId: BOB, memberIds: [BOB], joinCode: JOIN_CODE_2 }));
    await setDoc(doc(db, `kitchenInvites/${JOIN_CODE_2}`), invite(KITCHEN_2));
  });

describe('kitchen-owned data', () => {
  beforeEach(seedKitchens);

  it('lets a member write every declared subcollection of their kitchen', async () => {
    await assertSucceeds(setDoc(doc(bob, `kitchens/${KITCHEN_1}/pantry/item-1`), pantryItem()));
    await assertSucceeds(setDoc(doc(bob, `kitchens/${KITCHEN_1}/shoppingLists/list-1`), shoppingList()));
    await assertSucceeds(
      setDoc(doc(bob, `kitchens/${KITCHEN_1}/shoppingLists/list-1/items/item-1`), shoppingItem()),
    );
    await assertSucceeds(setDoc(doc(bob, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`), savedRecipe()));
  });

  it('lets the owner read what a member wrote', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}/pantry/item-1`), pantryItem());
      await setDoc(doc(db, `kitchens/${KITCHEN_1}/shoppingLists/list-1`), shoppingList());
      await setDoc(doc(db, `kitchens/${KITCHEN_1}/shoppingLists/list-1/items/item-1`), shoppingItem());
      await setDoc(doc(db, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`), savedRecipe());
    });

    await assertSucceeds(getDoc(doc(alice, `kitchens/${KITCHEN_1}/pantry/item-1`)));
    await assertSucceeds(getDocs(collection(alice, `kitchens/${KITCHEN_1}/shoppingLists`)));
    await assertSucceeds(getDoc(doc(alice, `kitchens/${KITCHEN_1}/shoppingLists/list-1/items/item-1`)));
    await assertSucceeds(getDoc(doc(alice, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`)));
  });

  it('denies a non-member every read of another kitchen\'s data', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_2}/pantry/item-1`), pantryItem());
      await setDoc(doc(db, `kitchens/${KITCHEN_2}/shoppingLists/list-1`), shoppingList());
      await setDoc(doc(db, `kitchens/${KITCHEN_2}/shoppingLists/list-1/items/item-1`), shoppingItem());
      await setDoc(doc(db, `kitchens/${KITCHEN_2}/savedRecipes/recipe-1`), savedRecipe());
    });

    // Alice belongs to kitchen-1 only; kitchen-2 is Bob's alone.
    await assertFails(getDoc(doc(alice, `kitchens/${KITCHEN_2}/pantry/item-1`)));
    await assertFails(getDocs(collection(alice, `kitchens/${KITCHEN_2}/shoppingLists`)));
    await assertFails(getDoc(doc(alice, `kitchens/${KITCHEN_2}/shoppingLists/list-1/items/item-1`)));
    await assertFails(getDoc(doc(alice, `kitchens/${KITCHEN_2}/savedRecipes/recipe-1`)));
  });

  it('denies a non-member every write of another kitchen\'s data', async () => {
    await assertFails(setDoc(doc(alice, `kitchens/${KITCHEN_2}/pantry/item-1`), pantryItem()));
    await assertFails(setDoc(doc(alice, `kitchens/${KITCHEN_2}/shoppingLists/list-1`), shoppingList()));
    await assertFails(
      setDoc(doc(alice, `kitchens/${KITCHEN_2}/shoppingLists/list-1/items/item-1`), shoppingItem()),
    );
    await assertFails(setDoc(doc(alice, `kitchens/${KITCHEN_2}/savedRecipes/recipe-1`), savedRecipe()));
  });

  it('denies a document in an undeclared subcollection', async () => {
    await assertFails(setDoc(doc(alice, `kitchens/${KITCHEN_1}/invented/doc-1`), { value: 1 }));
    await assertFails(getDoc(doc(alice, `kitchens/${KITCHEN_1}/invented/doc-1`)));
  });
});

describe('unauthenticated access', () => {
  beforeEach(async () => {
    await seedKitchens();
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}/pantry/item-1`), pantryItem());
      await setDoc(doc(db, 'taxonomies/taxonomy-1'), { labels: {} });
      await setDoc(doc(db, 'ingredients/ingredient-1'), { labels: {} });
      await setDoc(doc(db, 'recipes/recipe-1'), { title: 'recipe title' });
    });
  });

  it('reads nothing', async () => {
    await assertFails(getDoc(doc(anonymous, `users/${ALICE}`)));
    await assertFails(getDoc(doc(anonymous, `kitchens/${KITCHEN_1}`)));
    await assertFails(getDoc(doc(anonymous, `kitchens/${KITCHEN_1}/pantry/item-1`)));
    await assertFails(getDoc(doc(anonymous, `kitchenInvites/${JOIN_CODE_1}`)));
    await assertFails(getDoc(doc(anonymous, 'taxonomies/taxonomy-1')));
    await assertFails(getDoc(doc(anonymous, 'ingredients/ingredient-1')));
    await assertFails(getDoc(doc(anonymous, 'recipes/recipe-1')));
  });

  it('writes nothing', async () => {
    await assertFails(setDoc(doc(anonymous, `kitchens/${KITCHEN_1}/pantry/item-1`), pantryItem()));
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
  beforeEach(seedKitchens);

  const write = (data) => setDoc(doc(alice, `kitchens/${KITCHEN_1}/pantry/item-1`), data);

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
  beforeEach(seedKitchens);

  const write = (data) => setDoc(doc(alice, `kitchens/${KITCHEN_1}/shoppingLists/list-1/items/item-1`), data);

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
  beforeEach(seedKitchens);

  it('rejects an unexpected field on a list', async () => {
    await assertFails(
      setDoc(doc(alice, `kitchens/${KITCHEN_1}/shoppingLists/list-1`), shoppingList({ shared: true })),
    );
  });

  it('rejects a saved recipe without a title or with over-long content', async () => {
    const { title, ...untitled } = savedRecipe();
    await assertFails(setDoc(doc(alice, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`), untitled));
    await assertFails(
      setDoc(
        doc(alice, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`),
        savedRecipe({ title: 'a'.repeat(201) }),
      ),
    );
    await assertFails(
      setDoc(
        doc(alice, `kitchens/${KITCHEN_1}/savedRecipes/recipe-1`),
        savedRecipe({ steps: Array(101).fill('step') }),
      ),
    );
  });
});

describe('kitchen document', () => {
  beforeEach(seedKitchens);

  it('lets a signed-in caller create a kitchen with themself as sole owner and member', async () => {
    await assertSucceeds(
      setDoc(doc(alice, 'kitchens/new-kitchen'), kitchen({ ownerId: ALICE, memberIds: [ALICE], joinCode: 'new-code' })),
    );
  });

  it('rejects a create that names someone else as owner or seeds another member', async () => {
    await assertFails(
      setDoc(doc(alice, 'kitchens/new-kitchen'), kitchen({ ownerId: BOB, memberIds: [ALICE], joinCode: 'new-code' })),
    );
    await assertFails(
      setDoc(
        doc(alice, 'kitchens/new-kitchen'),
        kitchen({ ownerId: ALICE, memberIds: [ALICE, BOB], joinCode: 'new-code' }),
      ),
    );
  });

  it('lets a non-member get a kitchen by id but never list the collection', async () => {
    await assertSucceeds(getDoc(doc(bob, `kitchens/${KITCHEN_1}`)));
    await assertFails(getDocs(collection(bob, 'kitchens')));
  });

  it('lets a signed-in caller join by adding only themself to memberIds', async () => {
    // Alice is not yet a member of Bob's kitchen-2; joining adds only her own uid.
    await assertSucceeds(
      updateDoc(doc(alice, `kitchens/${KITCHEN_2}`), { memberIds: [BOB, ALICE] }),
    );
  });

  it('rejects a join that also renames someone else or changes ownerId or joinCode', async () => {
    await assertFails(updateDoc(doc(alice, `kitchens/${KITCHEN_2}`), { memberIds: [BOB, ALICE], ownerId: ALICE }));
    await assertFails(updateDoc(doc(alice, `kitchens/${KITCHEN_2}`), { memberIds: [BOB, ALICE], joinCode: 'stolen' }));
    await assertFails(
      updateDoc(doc(alice, `kitchens/${KITCHEN_2}`), {
        memberIds: [BOB, ALICE],
        'memberDisplayNames.user-alice': 'Alice',
        'memberDisplayNames.user-someone-else': 'Ghost',
      }),
    );
  });

  it('lets a member leave by removing only themself', async () => {
    await assertSucceeds(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE] }));
  });

  it('rejects a member removing someone other than themself', async () => {
    await assertFails(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { memberIds: [BOB] }));
  });

  it('lets only the owner remove another member, and blocklists the removed uid', async () => {
    await assertSucceeds(
      updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE], removedMemberIds: [BOB] }),
    );
    await assertFails(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE], removedMemberIds: [BOB] }));
  });

  it('rejects a removal write missing the matching removedMemberIds entry', async () => {
    await assertFails(updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE] }));
  });

  it('rejects a removal write that also blocklists an unrelated uid', async () => {
    await assertFails(
      updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE], removedMemberIds: [BOB, 'someone-else'] }),
    );
  });

  it('rejects a removed member rejoining even though they still know the kitchen id', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}`), kitchen({ memberIds: [ALICE], removedMemberIds: [BOB] }));
    });

    await assertFails(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { memberIds: [ALICE, BOB] }));
  });

  it('rejects the owner leaving a kitchen that still has other members', async () => {
    // kitchen-1 is seeded with alice as owner and bob as a member.
    await assertFails(updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { memberIds: [BOB] }));
  });

  it('lets the sole owner leave, producing an empty memberIds with no other path back', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}`), kitchen({ memberIds: [ALICE] }));
    });

    await assertSucceeds(updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { memberIds: [] }));
    await assertFails(deleteDoc(doc(alice, `kitchens/${KITCHEN_1}`)));
  });

  it('rejects a create that seeds a non-empty removedMemberIds', async () => {
    await assertFails(
      setDoc(
        doc(alice, 'kitchens/new-kitchen'),
        kitchen({ ownerId: ALICE, memberIds: [ALICE], joinCode: 'new-code', removedMemberIds: [BOB] }),
      ),
    );
  });

  it('lets only the owner regenerate the join code', async () => {
    await assertSucceeds(updateDoc(doc(alice, `kitchens/${KITCHEN_1}`), { joinCode: 'alice-new-code' }));
    await assertFails(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { joinCode: 'bob-forged-code' }));
  });

  it('lets a member write only their own memberDisplayNames entry', async () => {
    await assertSucceeds(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { 'memberDisplayNames.user-bob': 'Bob' }));
    await assertFails(updateDoc(doc(bob, `kitchens/${KITCHEN_1}`), { 'memberDisplayNames.user-alice': 'Not Bob' }));
  });

  it('is never deletable through client rules', async () => {
    await assertFails(deleteDoc(doc(alice, `kitchens/${KITCHEN_1}`)));
  });
});

// The write JoinKitchenUseCase ends in: leaving the current kitchen and joining the one a code
// names as one commit. `moveKitchens` mirrors FirestoreKitchenRepository.joinTransaction (every
// read first, no write at all when the invite does not resolve, then both documents written whole,
// defaults included), so these tests pin the shapes the rules must accept for that transaction.
const moveKitchens = (db, { uid, code, from, name = null }) =>
  runTransaction(db, async (tx) => {
    const inviteSnap = await tx.get(doc(db, `kitchenInvites/${code}`));
    if (!inviteSnap.exists()) return 'no-invite';
    const targetId = inviteSnap.data().kitchenId;
    const targetSnap = await tx.get(doc(db, `kitchens/${targetId}`));
    const fromSnap = await tx.get(doc(db, `kitchens/${from}`));
    const left = fromSnap.data();
    const names = { ...left.memberDisplayNames };
    delete names[uid];
    tx.set(doc(db, `kitchens/${from}`), {
      ...left,
      memberIds: left.memberIds.filter((member) => member !== uid),
      memberDisplayNames: names,
      removedMemberIds: left.removedMemberIds ?? [],
    });
    const target = targetSnap.data();
    tx.set(doc(db, `kitchens/${targetId}`), {
      ...target,
      memberIds: [...target.memberIds, uid],
      memberDisplayNames: { ...target.memberDisplayNames, ...(name ? { [uid]: name } : {}) },
      removedMemberIds: target.removedMemberIds ?? [],
    });
    return 'moved';
  });

// `withSecurityRulesDisabled` hands nothing back, so the document is carried out through a closure.
const readKitchen = async (id) => {
  let data;
  await seed(async (db) => {
    data = (await getDoc(doc(db, `kitchens/${id}`))).data();
  });
  return data;
};

describe('leaving one kitchen and joining another in a single transaction', () => {
  // Carol is a plain member of Alice's kitchen-1; Bob's kitchen-2 is the one she moves to.
  beforeEach(async () => {
    await seedKitchens();
    await seed(async (db) => {
      await setDoc(
        doc(db, `kitchens/${KITCHEN_1}`),
        kitchen({ memberIds: [ALICE, CAROL], memberDisplayNames: { [CAROL]: 'Carol' } }),
      );
    });
  });

  it('commits both writes, naming the joiner in the new kitchen only', async () => {
    const outcome = await assertSucceeds(
      moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1, name: 'Carol' }),
    );

    assert.equal(outcome, 'moved');
    const left = await readKitchen(KITCHEN_1);
    assert.deepEqual(left.memberIds, [ALICE]);
    assert.deepEqual(left.memberDisplayNames, {});
    const joined = await readKitchen(KITCHEN_2);
    assert.deepEqual(joined.memberIds, [BOB, CAROL]);
    assert.deepEqual(joined.memberDisplayNames, { [CAROL]: 'Carol' });
  });

  it('joins without a name when the profile has none', async () => {
    await assertSucceeds(moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1 }));

    const joined = await readKitchen(KITCHEN_2);
    assert.deepEqual(joined.memberIds, [BOB, CAROL]);
    assert.deepEqual(joined.memberDisplayNames, {});
  });

  it('writes nothing when the code does not resolve, so membership is untouched', async () => {
    const outcome = await assertSucceeds(moveKitchens(carol, { uid: CAROL, code: 'unknown-code', from: KITCHEN_1 }));

    assert.equal(outcome, 'no-invite');
    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, [ALICE, CAROL]);
    assert.deepEqual((await readKitchen(KITCHEN_2)).memberIds, [BOB]);
  });

  it('writes nothing for a code whose invite was rotated away', async () => {
    await seed(async (db) => deleteDoc(doc(db, `kitchenInvites/${JOIN_CODE_2}`)));

    const outcome = await assertSucceeds(moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1 }));

    assert.equal(outcome, 'no-invite');
    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, [ALICE, CAROL]);
  });

  it('rolls the leave back when the target refuses the joiner', async () => {
    await seed(async (db) => {
      await setDoc(
        doc(db, `kitchens/${KITCHEN_2}`),
        kitchen({ ownerId: BOB, memberIds: [BOB], joinCode: JOIN_CODE_2, removedMemberIds: [CAROL] }),
      );
    });

    await assertFails(moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1 }));

    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, [ALICE, CAROL]);
    assert.deepEqual((await readKitchen(KITCHEN_2)).memberIds, [BOB]);
  });

  it('rejects the owner of a kitchen with other members leaving it this way, and joins nothing', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}`), kitchen({ ownerId: CAROL, memberIds: [CAROL, ALICE] }));
    });

    await assertFails(moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1 }));

    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, [CAROL, ALICE]);
    assert.deepEqual((await readKitchen(KITCHEN_2)).memberIds, [BOB]);
  });

  it('lets a sole owner move, leaving an empty kitchen behind', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, `kitchens/${KITCHEN_1}`), kitchen({ ownerId: CAROL, memberIds: [CAROL] }));
    });

    await assertSucceeds(moveKitchens(carol, { uid: CAROL, code: JOIN_CODE_2, from: KITCHEN_1, name: 'Carol' }));

    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, []);
    assert.deepEqual((await readKitchen(KITCHEN_2)).memberIds, [BOB, CAROL]);
  });

  it('rejects a move that also renames someone else in the target', async () => {
    await assertFails(
      runTransaction(carol, async (tx) => {
        const fromSnap = await tx.get(doc(carol, `kitchens/${KITCHEN_1}`));
        const targetSnap = await tx.get(doc(carol, `kitchens/${KITCHEN_2}`));
        tx.set(doc(carol, `kitchens/${KITCHEN_1}`), { ...fromSnap.data(), memberIds: [ALICE], memberDisplayNames: {} });
        tx.set(doc(carol, `kitchens/${KITCHEN_2}`), {
          ...targetSnap.data(),
          memberIds: [BOB, CAROL],
          memberDisplayNames: { [CAROL]: 'Carol', [BOB]: 'Renamed by Carol' },
        });
      }),
    );

    assert.deepEqual((await readKitchen(KITCHEN_1)).memberIds, [ALICE, CAROL]);
  });
});

describe('kitchen invites', () => {
  beforeEach(seedKitchens);

  it('lets any signed-in caller resolve a code by get, but never list the collection', async () => {
    await assertSucceeds(getDoc(doc(bob, `kitchenInvites/${JOIN_CODE_1}`)));
    await assertFails(getDocs(collection(alice, 'kitchenInvites')));
  });

  it('lets only the owner create, update or delete an invite for their kitchen', async () => {
    await assertSucceeds(setDoc(doc(alice, 'kitchenInvites/alice-new-code'), invite(KITCHEN_1)));
    await assertFails(setDoc(doc(bob, 'kitchenInvites/bob-forged-code'), invite(KITCHEN_1)));

    await assertSucceeds(setDoc(doc(alice, `kitchenInvites/${JOIN_CODE_1}`), invite(KITCHEN_1)));
    await assertFails(setDoc(doc(bob, `kitchenInvites/${JOIN_CODE_1}`), invite(KITCHEN_1)));

    await assertFails(deleteDoc(doc(bob, `kitchenInvites/${JOIN_CODE_1}`)));
    await assertSucceeds(deleteDoc(doc(alice, `kitchenInvites/${JOIN_CODE_1}`)));
  });

  // The write createKitchen issues. In production get() reads the state before the commit, so the
  // invite rule must judge the kitchen as it will be after the write (getAfter).
  it('lets a new owner create their kitchen and its invite in one atomic write', async () => {
    const batch = writeBatch(carol);
    batch.set(doc(carol, 'kitchens/carol-kitchen'), kitchen({ ownerId: CAROL, memberIds: [CAROL], joinCode: 'carol-code' }));
    batch.set(doc(carol, 'kitchenInvites/carol-code'), invite('carol-kitchen'));
    await assertSucceeds(batch.commit());
  });

  it('rejects an atomic write whose invite names a kitchen that someone else owns', async () => {
    const batch = writeBatch(carol);
    batch.set(doc(carol, 'kitchens/carol-kitchen'), kitchen({ ownerId: CAROL, memberIds: [CAROL], joinCode: 'carol-code' }));
    batch.set(doc(carol, 'kitchenInvites/hijack-code'), invite(KITCHEN_1));
    await assertFails(batch.commit());
  });

  it('rejects an invite for a kitchen that does not exist and is not created with it', async () => {
    await assertFails(setDoc(doc(carol, 'kitchenInvites/orphan-code'), invite('no-such-kitchen')));
  });

  it('rejects an invite naming a kitchen the caller does not own', async () => {
    await assertFails(setDoc(doc(alice, 'kitchenInvites/forged-code'), invite(KITCHEN_2)));
  });

  it('rejects an unexpected field', async () => {
    await assertFails(
      setDoc(doc(alice, 'kitchenInvites/alice-new-code'), { ...invite(KITCHEN_1), extra: true }),
    );
  });
});
