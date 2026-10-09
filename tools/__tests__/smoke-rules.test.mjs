import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ID_PREFIX,
  UsageError,
  assertCleanable,
  buildSteps,
  classify,
  commitBody,
  decodeFields,
  encodeFields,
  evaluate,
  fillFromGoogleServices,
  formatLine,
  parseSmokeArgs,
  redact,
  resolveTarget,
  runIds,
  touchedPaths,
} from '../lib/smoke-rules.mjs';

const ids = runIds('t1', 'uid-owner', 'uid-member');
const steps = buildSteps(ids);
const named = (fragment) => steps.find((step) => step.name.includes(fragment));
const writesOf = (step) => step.op.writes.map((write) => write.set?.path ?? `delete ${write.delete}`);

const denied = { status: 403, body: { error: { code: 403, status: 'PERMISSION_DENIED', message: 'Missing or insufficient permissions.' } } };
const blockedKey = {
  status: 403,
  body: { error: { status: 'PERMISSION_DENIED', message: 'blocked', details: [{ reason: 'API_KEY_ANDROID_APP_BLOCKED' }] } },
};

test('arguments: flags win over the environment, and nothing names a project by default', () => {
  const args = parseSmokeArgs(['--project', 'from-flag'], { KITCHENAI_PROJECT: 'from-env', KITCHENAI_API_KEY: 'k' });
  assert.equal(args.project, 'from-flag');
  assert.equal(args.apiKey, 'k');
  assert.equal(parseSmokeArgs([], {}).project, null);
  assert.throws(() => resolveTarget(parseSmokeArgs([], {})), UsageError);
});

test('arguments: a missing key is refused for a project but not for an emulator', () => {
  assert.throws(() => resolveTarget(parseSmokeArgs(['--project', 'p'], {})), /api-key/);
  const emulated = resolveTarget(
    parseSmokeArgs(['--project', 'demo-p'], { FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080', FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099' }),
  );
  assert.equal(emulated.emulated, true);
  assert.match(emulated.firestoreBase, /^http:\/\/127\.0\.0\.1:8080\/v1\/projects\/demo-p\//);
});

test('arguments: one emulator host without the other is refused', () => {
  assert.throws(
    () => resolveTarget(parseSmokeArgs(['--project', 'p'], { FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080' })),
    /both/,
  );
});

test('arguments: the config file fills only what flags and environment left empty', () => {
  const config = {
    project_info: { project_id: 'cfg-project' },
    client: [{ api_key: [{ current_key: 'cfg-key' }], client_info: { android_client_info: { package_name: 'cfg.pkg' } } }],
  };
  const filled = fillFromGoogleServices(parseSmokeArgs(['--project', 'flag-project'], {}), config);
  assert.deepEqual([filled.project, filled.apiKey, filled.androidPackage], ['flag-project', 'cfg-key', 'cfg.pkg']);
});

test('arguments: the debug certificate becomes the Android headers the SDK sends', () => {
  const sha1 = Array(20).fill('ab').join(':');
  const target = resolveTarget(
    parseSmokeArgs(['--project', 'p', '--api-key', 'k', '--android-package', 'a.b', '--cert-sha1', sha1], {}),
  );
  assert.deepEqual(target.androidHeaders, { 'X-Android-Package': 'a.b', 'X-Android-Cert': 'AB'.repeat(20) });
  assert.throws(() => resolveTarget(parseSmokeArgs(['--project', 'p', '--api-key', 'k', '--cert-sha1', 'aa'], {})), /package/);
  assert.throws(
    () => resolveTarget(parseSmokeArgs(['--project', 'p', '--api-key', 'k', '--android-package', 'a.b', '--cert-sha1', 'aa:bb'], {})),
    /40 hex/,
  );
  assert.deepEqual(resolveTarget(parseSmokeArgs(['--project', 'p', '--api-key', 'k'], {})).androidHeaders, {});
});

test('arguments: an unknown flag or a positional is a usage error', () => {
  assert.throws(() => parseSmokeArgs(['--nope'], {}), UsageError);
  assert.throws(() => parseSmokeArgs(['extra'], {}), UsageError);
});

test('values survive a round trip through the REST encoding', () => {
  const data = { ownerId: 'u', memberIds: ['a', 'b'], memberDisplayNames: { a: 'x' }, removedMemberIds: [] };
  assert.deepEqual(decodeFields(encodeFields(data)), data);
  assert.throws(() => encodeFields({ n: 1 }));
});

test('a commit body names fully qualified documents, sets whole documents and deletes by name', () => {
  const body = commitBody('p', [{ set: { path: 'kitchens/k', data: { a: 'b' } } }, { delete: 'kitchenInvites/c' }]);
  assert.equal(body.writes[0].update.name, 'projects/p/databases/(default)/documents/kitchens/k');
  assert.equal(body.writes[0].updateMask, undefined);
  assert.equal(body.writes[1].delete, 'projects/p/databases/(default)/documents/kitchenInvites/c');
});

test('every id the run creates carries the prefix, and nothing else is written', () => {
  for (const path of touchedPaths(steps)) {
    assert.match(path.split('/')[1], new RegExp(`^${ID_PREFIX}`));
    assert.doesNotThrow(() => assertCleanable(path));
  }
  assert.equal(touchedPaths(steps).length, 6);
});

test('the cleanup refuses a document the script does not create', () => {
  assert.throws(() => assertCleanable('kitchens/real-kitchen'), /refusing/);
  assert.throws(() => assertCleanable('users/smoke-x'), /refusing/);
  assert.throws(() => assertCleanable('kitchens/smoke-x/pantry/smoke-y'), /refusing/);
});

test('the table covers every write of the first launch, in order, with the refusals marked', () => {
  assert.deepEqual(
    steps.map((step) => step.expect),
    ['allow', 'allow', 'deny', 'found', 'allow', 'allow', 'deny', 'found', 'allow', 'missing', 'found'],
  );
  assert.deepEqual(writesOf(steps[0]), [`kitchens/${ids.ownerKitchen}`, `kitchenInvites/${ids.ownerCode}`]);
});

test('joining leaves the old kitchen and joins the new one in the same commit', () => {
  const step = named('ONE commit');
  assert.deepEqual(writesOf(step), [`kitchens/${ids.memberKitchen}`, `kitchens/${ids.ownerKitchen}`]);
  const [leave, join] = step.op.writes.map((write) => write.set.data);
  assert.deepEqual(leave.memberIds, []);
  assert.deepEqual(join.memberIds, [ids.ownerUid, ids.memberUid]);
});

test('the rejoin keeps the member in removedMemberIds and only adds them back to memberIds', () => {
  const rejoin = named('rejoin').op.writes[0].set.data;
  assert.deepEqual(rejoin.removedMemberIds, [ids.memberUid]);
  assert.deepEqual(rejoin.memberIds, [ids.ownerUid, ids.memberUid]);
});

test('regenerating the code deletes the old invite and creates the new one in one commit', () => {
  assert.deepEqual(writesOf(named('regenerates')), [
    `kitchens/${ids.ownerKitchen}`,
    `delete kitchenInvites/${ids.ownerCode}`,
    `kitchenInvites/${ids.newCode}`,
  ]);
});

test('a 403 counts as the rules denying only when it carries no machine reason', () => {
  const commit = { type: 'commit' };
  assert.equal(classify(commit, denied), 'deny');
  assert.equal(classify(commit, blockedKey), 'error');
  assert.equal(classify(commit, { status: 200, body: {} }), 'allow');
  assert.equal(classify(commit, { status: 404, body: {} }), 'error');
  assert.equal(classify({ type: 'get' }, { status: 404, body: {} }), 'missing');
  assert.equal(classify({ type: 'get' }, { status: 200, body: {} }), 'found');
  assert.equal(classify(commit, { status: 429, body: {} }), 'error');
});

test('a step passes only on the outcome it expects', () => {
  const allow = steps[0];
  const deny = named('rejoin');
  assert.equal(evaluate(allow, { status: 200, body: {} }).ok, true);
  assert.match(evaluate(allow, denied).detail, /expected allow, got deny/);
  assert.equal(evaluate(deny, denied).ok, true);
  assert.match(evaluate(deny, { status: 200, body: {} }).detail, /expected deny, got allow/);
  // An expected refusal must not be satisfied by a broken request.
  assert.equal(evaluate(deny, blockedKey).ok, false);
  assert.equal(evaluate(deny, { status: 400, body: { error: { status: 'INVALID_ARGUMENT' } } }).ok, false);
});

test('a found document is judged on its content', () => {
  const state = named('changed nothing');
  const body = (memberIds, removed) => ({ fields: encodeFields({ memberIds, removedMemberIds: removed }) });
  assert.equal(evaluate(state, { status: 200, body: body([ids.ownerUid], [ids.memberUid]) }).ok, true);
  assert.equal(evaluate(state, { status: 200, body: body([ids.ownerUid, ids.memberUid], [ids.memberUid]) }).ok, false);
  assert.equal(evaluate(state, { status: 200, body: { fields: encodeFields({ memberIds: [ids.ownerUid] }) } }).ok, false);
});

test('redaction removes the project and the key from anything printed', () => {
  assert.equal(redact('call to proj-1 with key-9 failed', ['proj-1', 'key-9', null]), 'call to <redacted> with <redacted> failed');
});

test('a result line states the position, the step and the outcome', () => {
  assert.equal(formatLine(3, { name: 'x' }, { ok: true, detail: 'deny' }), 'ok   03 x (deny)');
  assert.match(formatLine(4, { name: 'x' }, { ok: false, detail: 'boom' }), /^FAIL 04 x \(boom\)$/);
});
