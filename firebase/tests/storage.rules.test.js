// Storage rules tests, run with the Firestore ones by `npm test`; see ../README.md.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteObject, getBytes, ref, uploadBytes } from 'firebase/storage';

const here = dirname(fileURLToPath(import.meta.url));

const PICTURE = new Uint8Array([1, 2, 3]);
const TOO_BIG = new Uint8Array(2 * 1024 * 1024);

let testEnv;
let admin;
let stringClaim;
let user;
let anonymous;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-kitchenai-rules',
    storage: {
      rules: readFileSync(join(here, '..', 'storage.rules'), 'utf8'),
      host: '127.0.0.1',
      port: 9199,
    },
  });
  admin = testEnv.authenticatedContext('user-admin', { admin: true }).storage();
  stringClaim = testEnv.authenticatedContext('user-string-claim', { admin: 'true' }).storage();
  user = testEnv.authenticatedContext('user-alice').storage();
  anonymous = testEnv.unauthenticatedContext().storage();
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearStorage();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), 'catalogue/ingredients/picture-1.webp'), PICTURE, {
      contentType: 'image/webp',
    });
  });
});

const upload = (storage, path, bytes = PICTURE, contentType = 'image/webp') =>
  uploadBytes(ref(storage, path), bytes, { contentType });

describe('catalogue pictures', () => {
  it('are readable by any signed-in client and nobody else', async () => {
    await assertSucceeds(getBytes(ref(user, 'catalogue/ingredients/picture-1.webp')));
    await assertFails(getBytes(ref(anonymous, 'catalogue/ingredients/picture-1.webp')));
  });

  it('are written and deleted by the admin', async () => {
    await assertSucceeds(upload(admin, 'catalogue/ingredients/picture-2.webp'));
    await assertSucceeds(upload(admin, 'catalogue/recipes/picture-2.png', PICTURE, 'image/png'));
    await assertSucceeds(deleteObject(ref(admin, 'catalogue/ingredients/picture-1.webp')));
  });

  it('are written by no one without the admin claim', async () => {
    for (const storage of [user, stringClaim, anonymous]) {
      await assertFails(upload(storage, 'catalogue/ingredients/picture-2.webp'));
      await assertFails(deleteObject(ref(storage, 'catalogue/ingredients/picture-1.webp')));
    }
  });

  it('refuse what is not a small picture in a known folder, even from the admin', async () => {
    await assertFails(upload(admin, 'catalogue/ingredients/file.txt', PICTURE, 'text/plain'));
    await assertFails(upload(admin, 'catalogue/ingredients/picture-2.webp', TOO_BIG));
    await assertFails(upload(admin, 'catalogue/other/picture-2.webp'));
    await assertFails(upload(admin, 'recipe-images/picture-2.webp'));
  });
});
