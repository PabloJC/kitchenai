import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import {
  UsageError,
  callableUrl,
  describeFailure,
  describeResult,
  parseCatalogueArgs,
  parseGrantArgs,
  parsePayload,
  validatePayload,
} from '../lib/catalogue-cli.mjs';
import { parseCatalogueEntry } from '../../functions/src/catalogueEntry.ts';

const seed = JSON.parse(readFileSync(new URL('../../firebase/seed/ingredients.json', import.meta.url), 'utf8'));

const recipe = {
  title: 'Tomato pasta',
  servings: 2,
  ingredients: [{ ingredientId: 'pasta', amount: 200, unitTaxonomy: 'units', unitTerm: 'gram' }],
  steps: ['Boil the pasta.'],
};

const rejected = [
  { kind: 'ingredient', id: 'egg', data: { labels: {} } },
  { kind: 'ingredient', id: 'egg', data: { labels: { en: 'Egg' }, surprise: 1 } },
  { kind: 'ingredient', id: '__reserved__', data: { labels: { en: 'Egg' } } },
  { kind: 'ingredient', id: 'free text', data: { labels: { en: 'Egg' } } },
  { kind: 'recipe', id: 'r', data: { ...recipe, steps: [] } },
  { kind: 'recipe', id: 'r', data: { ...recipe, source: { type: 'agent' } } },
  { kind: 'recipe', id: 'r', data: { ...recipe, ingredients: [{ ingredientId: 'a', freeText: 'b' }] } },
  { kind: 'recipe', id: 'r', data: { ...recipe, ingredients: [{ freeText: 'salt', unitTaxonomy: 'units', unitTerm: 'gram' }] } },
  { kind: 'drink', id: 'r', data: {} },
  'not an object',
  null,
];

test('the dry-run accepts every seed ingredient, as the function does', async () => {
  for (const [id, data] of Object.entries(seed)) {
    const raw = { kind: 'ingredient', id, data };
    assert.deepEqual(await validatePayload(raw), { ok: true, kind: 'ingredient', id }, id);
  }
});

test('the dry-run rejects with the same message the function would', async () => {
  for (const raw of rejected) {
    const verdict = await validatePayload(raw);
    assert.equal(verdict.ok, false, JSON.stringify(raw));
    assert.throws(() => parseCatalogueEntry(raw), { message: verdict.message });
  }
});

test('a valid recipe passes the dry-run', async () => {
  assert.deepEqual(await validatePayload({ kind: 'recipe', id: 'tomato-pasta', data: recipe }), {
    ok: true,
    kind: 'recipe',
    id: 'tomato-pasta',
  });
});

test('payload text must be non-empty JSON', () => {
  assert.deepEqual(parsePayload('{"a":1}'), { a: 1 });
  assert.throws(() => parsePayload('  '), UsageError);
  assert.throws(() => parsePayload('{nope'), UsageError);
});

test('a dry-run needs no project and no credentials', () => {
  const args = parseCatalogueArgs(['--dry-run', 'entry.json'], {});
  assert.equal(args.dryRun, true);
  assert.equal(args.file, 'entry.json');
});

test('a real write needs a project and a way to sign in', () => {
  assert.throws(() => parseCatalogueArgs(['entry.json'], { KITCHENAI_ID_TOKEN: 't' }), /project/);
  assert.throws(() => parseCatalogueArgs(['--project', 'p'], {}), /sign-in/);
  assert.throws(() => parseCatalogueArgs(['--project', 'p', '--uid', 'u'], {}), /sign-in/);
});

test('flags win over the environment, and the token is environment only', () => {
  const env = { KITCHENAI_PROJECT: 'from-env', KITCHENAI_REGION: 'r-env', KITCHENAI_ID_TOKEN: 'tok' };
  const args = parseCatalogueArgs(['--project', 'from-flag'], env);
  assert.equal(args.project, 'from-flag');
  assert.equal(args.region, 'r-env');
  assert.equal(args.idToken, 'tok');
  assert.equal(args.file, '-');
  assert.throws(() => parseCatalogueArgs(['--id-token', 'x', '--project', 'p'], env), UsageError);
});

test('sign-in by uid and api key is enough without a token', () => {
  const args = parseCatalogueArgs(['--project', 'p', '--uid', 'u', '--api-key', 'k', 'f.json'], {});
  assert.equal(args.uid, 'u');
  assert.equal(args.apiKey, 'k');
});

test('grant-admin has no default project and requires a uid', () => {
  assert.throws(() => parseGrantArgs(['--uid', 'u'], {}), /project/);
  assert.throws(() => parseGrantArgs(['--project', 'p'], {}), /uid/);
  assert.deepEqual(parseGrantArgs(['--project', 'p', '--uid', 'u', '--revoke'], {}), {
    help: false,
    revoke: true,
    project: 'p',
    uid: 'u',
  });
});

test('the callable url is built from the project and region', () => {
  assert.equal(callableUrl('p', 'r'), 'https://r-p.cloudfunctions.net/writeCatalogue');
});

test('the result says whether an existing id was replaced', () => {
  assert.match(describeResult({ kind: 'recipe', id: 'x', created: true }), /^created recipe "x"/);
  assert.match(describeResult({ kind: 'recipe', id: 'x', created: false }), /replaced the existing recipe "x"/);
});

test('callable failures map to an actionable message', () => {
  const fail = (status) => describeFailure(400, { error: status });
  assert.match(fail({ status: 'PERMISSION_DENIED' }), /no admin claim/);
  assert.match(fail({ status: 'UNAUTHENTICATED' }), /not signed in/);
  assert.match(fail({ status: 'INVALID_ARGUMENT', message: 'id is reserved' }), /id is reserved/);
  assert.match(describeFailure(500, {}), /HTTP 500/);
});
