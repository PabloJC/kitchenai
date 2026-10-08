// The pure parts of tools/smoke-rules.mjs: argument handling, the step table and the verdict on
// each response. No network and no credentials, so all of it can be tested.

import { parseArgs } from 'node:util';

export class UsageError extends Error {}

/** Every id the script creates starts with this; the cleanup refuses anything that does not. */
export const ID_PREFIX = 'smoke-';

const CLEANABLE = new Set(['kitchens', 'kitchenInvites']);

function parse(argv, options) {
  try {
    return parseArgs({ args: argv, options, allowPositionals: true });
  } catch (failure) {
    throw new UsageError(failure.message);
  }
}

/** Flags win over the environment; nothing here has a default that names a project. */
export function parseSmokeArgs(argv, env = {}) {
  const { values, positionals } = parse(argv, {
    project: { type: 'string' },
    'api-key': { type: 'string' },
    'android-package': { type: 'string' },
    'cert-sha1': { type: 'string' },
    config: { type: 'string' },
    cleanup: { type: 'boolean', default: false },
    help: { type: 'boolean', default: false },
  });
  if (positionals.length > 0) throw new UsageError('unexpected argument');
  return {
    help: values.help,
    cleanup: values.cleanup,
    project: values.project ?? env.KITCHENAI_PROJECT ?? null,
    apiKey: values['api-key'] ?? env.KITCHENAI_API_KEY ?? null,
    androidPackage: values['android-package'] ?? env.KITCHENAI_ANDROID_PACKAGE ?? null,
    certSha1: values['cert-sha1'] ?? env.KITCHENAI_CERT_SHA1 ?? null,
    config: values.config ?? env.KITCHENAI_GOOGLE_SERVICES ?? null,
    // The standard emulator variables: set both and the whole run stays on this machine.
    emulator: { firestore: env.FIRESTORE_EMULATOR_HOST ?? null, auth: env.FIREBASE_AUTH_EMULATOR_HOST ?? null },
  };
}

/** Fills what the flags and the environment left empty from a parsed google-services.json. */
export function fillFromGoogleServices(args, config) {
  const client = config?.client?.[0];
  return {
    ...args,
    project: args.project ?? config?.project_info?.project_id ?? null,
    apiKey: args.apiKey ?? client?.api_key?.[0]?.current_key ?? null,
    androidPackage: args.androidPackage ?? client?.client_info?.android_client_info?.package_name ?? null,
  };
}

/** The settings a run needs, or a UsageError saying which one is missing. */
export function resolveTarget(args) {
  const { firestore, auth } = args.emulator;
  if (Boolean(firestore) !== Boolean(auth)) {
    throw new UsageError('set both FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST, or neither');
  }
  // No default on purpose: the wrong project here is a run against somebody else's data.
  if (!args.project) throw new UsageError('--project <projectId> (or KITCHENAI_PROJECT, or --config) is required');
  const emulated = Boolean(firestore);
  if (!emulated && !args.apiKey) {
    throw new UsageError('--api-key (or KITCHENAI_API_KEY, or --config) is required to sign in');
  }
  if (args.certSha1 && !args.androidPackage) {
    throw new UsageError('--cert-sha1 needs the Android package: --android-package or --config');
  }
  if (args.certSha1 && !/^[0-9A-F]{40}$/.test(args.certSha1.replaceAll(':', '').toUpperCase())) {
    throw new UsageError('--cert-sha1 must be a SHA-1 fingerprint: 40 hex digits, colons optional');
  }
  return {
    project: args.project,
    apiKey: args.apiKey ?? 'emulator',
    emulated,
    firestoreBase: emulated
      ? `http://${firestore}/v1/projects/${args.project}/databases/(default)/documents`
      : `https://firestore.googleapis.com/v1/projects/${args.project}/databases/(default)/documents`,
    identityBase: emulated
      ? `http://${auth}/identitytoolkit.googleapis.com/v1`
      : 'https://identitytoolkit.googleapis.com/v1',
    androidHeaders: androidHeaders(args),
    cleanup: args.cleanup,
  };
}

/** What the Android SDK sends, so a key restricted to the app accepts a call from a laptop. */
export function androidHeaders({ androidPackage, certSha1 }) {
  if (!androidPackage || !certSha1) return {};
  return { 'X-Android-Package': androidPackage, 'X-Android-Cert': certSha1.replaceAll(':', '').toUpperCase() };
}

// ---------------------------------------------------------------- //
// Firestore REST values
// ---------------------------------------------------------------- //

/** Only the shapes the kitchen and invite documents use: strings, lists and maps. */
export function encodeValue(value) {
  if (typeof value === 'string') return { stringValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encodeValue) } };
  if (value !== null && typeof value === 'object') return { mapValue: { fields: encodeFields(value) } };
  throw new Error(`cannot encode ${typeof value}`);
}

export function encodeFields(object) {
  return Object.fromEntries(Object.entries(object).map(([key, value]) => [key, encodeValue(value)]));
}

export function decodeValue(value) {
  if ('stringValue' in value) return value.stringValue;
  if ('arrayValue' in value) return (value.arrayValue.values ?? []).map(decodeValue);
  if ('mapValue' in value) return decodeFields(value.mapValue.fields ?? {});
  throw new Error('unexpected value type');
}

export function decodeFields(fields) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, decodeValue(value)]));
}

/** The `documents:commit` body for a list of `{ set: { path, data } }` / `{ delete: path }`. */
export function commitBody(project, writes) {
  const name = (path) => `projects/${project}/databases/(default)/documents/${path}`;
  return {
    writes: writes.map((write) =>
      write.set
        ? { update: { name: name(write.set.path), fields: encodeFields(write.set.data) } }
        : { delete: name(write.delete) },
    ),
  };
}

// ---------------------------------------------------------------- //
// The scenario
// ---------------------------------------------------------------- //

/** The ids of one run; the tag keeps two runs, and anything real, apart. */
export function runIds(tag, ownerUid, memberUid) {
  const id = (name) => `${ID_PREFIX}${tag}-${name}`;
  return {
    ownerUid,
    memberUid,
    ownerKitchen: id('owner-kitchen'),
    memberKitchen: id('member-kitchen'),
    ownerCode: id('owner-code'),
    memberCode: id('member-code'),
    newCode: id('new-code'),
    hijackCode: id('hijack-code'),
  };
}

const sameList = (actual, expected) =>
  Array.isArray(actual) && actual.length === expected.length && actual.every((item, at) => item === expected[at]);

/**
 * The writes the app makes, in the shapes `FirestoreKitchenRepository` produces (encodeDefaults
 * on, so empty lists and maps are written). `expect` is `allow` / `deny` for a commit and
 * `found` / `missing` for a read; `check` inspects a document that was found.
 */
export function buildSteps(ids) {
  const { ownerUid: owner, memberUid: member } = ids;
  const kitchen = (ownerId, memberIds, joinCode, names, removed = []) => ({
    ownerId,
    memberIds,
    joinCode,
    memberDisplayNames: Object.fromEntries(names.map((uid) => [uid, `${ID_PREFIX}${uid === owner ? 'owner' : 'member'}`])),
    removedMemberIds: removed,
  });
  const invite = (kitchenId) => ({ kitchenId });
  const kitchenPath = (id) => `kitchens/${id}`;
  const invitePath = (code) => `kitchenInvites/${code}`;

  return [
    {
      name: 'owner creates a kitchen and its invite in one atomic commit',
      actor: 'owner',
      op: {
        type: 'commit',
        writes: [
          { set: { path: kitchenPath(ids.ownerKitchen), data: kitchen(owner, [owner], ids.ownerCode, [owner]) } },
          { set: { path: invitePath(ids.ownerCode), data: invite(ids.ownerKitchen) } },
        ],
      },
      expect: 'allow',
    },
    {
      name: 'member creates their own kitchen and invite the same way',
      actor: 'member',
      op: {
        type: 'commit',
        writes: [
          { set: { path: kitchenPath(ids.memberKitchen), data: kitchen(member, [member], ids.memberCode, [member]) } },
          { set: { path: invitePath(ids.memberCode), data: invite(ids.memberKitchen) } },
        ],
      },
      expect: 'allow',
    },
    {
      name: "member cannot create an invite that names the owner's kitchen",
      actor: 'member',
      op: { type: 'commit', writes: [{ set: { path: invitePath(ids.hijackCode), data: invite(ids.ownerKitchen) } }] },
      expect: 'deny',
    },
    {
      name: "member resolves the owner's code to the owner's kitchen",
      actor: 'member',
      op: { type: 'get', path: invitePath(ids.ownerCode) },
      expect: 'found',
      check: (doc) => (doc.kitchenId === ids.ownerKitchen ? null : 'the invite names a different kitchen'),
    },
    {
      name: 'member leaves their own kitchen and joins the owner in ONE commit',
      actor: 'member',
      op: {
        type: 'commit',
        writes: [
          { set: { path: kitchenPath(ids.memberKitchen), data: kitchen(member, [], ids.memberCode, []) } },
          {
            set: {
              path: kitchenPath(ids.ownerKitchen),
              data: kitchen(owner, [owner, member], ids.ownerCode, [owner, member]),
            },
          },
        ],
      },
      expect: 'allow',
    },
    {
      name: 'owner removes the member',
      actor: 'owner',
      op: {
        type: 'commit',
        writes: [
          { set: { path: kitchenPath(ids.ownerKitchen), data: kitchen(owner, [owner], ids.ownerCode, [owner], [member]) } },
        ],
      },
      expect: 'allow',
    },
    {
      name: "removed member's rejoin is refused",
      actor: 'member',
      op: {
        type: 'commit',
        writes: [
          {
            set: {
              path: kitchenPath(ids.ownerKitchen),
              data: kitchen(owner, [owner, member], ids.ownerCode, [owner, member], [member]),
            },
          },
        ],
      },
      expect: 'deny',
    },
    {
      name: 'the refused rejoin changed nothing',
      actor: 'owner',
      op: { type: 'get', path: kitchenPath(ids.ownerKitchen) },
      expect: 'found',
      check: (doc) =>
        sameList(doc.memberIds, [owner]) && sameList(doc.removedMemberIds, [member])
          ? null
          : 'the kitchen does not hold exactly the owner as member and the removed member as blocked',
    },
    {
      name: 'owner regenerates the code: new invite, old invite deleted, one commit',
      actor: 'owner',
      op: {
        type: 'commit',
        writes: [
          { set: { path: kitchenPath(ids.ownerKitchen), data: kitchen(owner, [owner], ids.newCode, [owner], [member]) } },
          { delete: invitePath(ids.ownerCode) },
          { set: { path: invitePath(ids.newCode), data: invite(ids.ownerKitchen) } },
        ],
      },
      expect: 'allow',
    },
    {
      name: 'the old code no longer resolves',
      actor: 'owner',
      op: { type: 'get', path: invitePath(ids.ownerCode) },
      expect: 'missing',
    },
    {
      name: 'the new code resolves to the kitchen',
      actor: 'owner',
      op: { type: 'get', path: invitePath(ids.newCode) },
      expect: 'found',
      check: (doc) => (doc.kitchenId === ids.ownerKitchen ? null : 'the invite names a different kitchen'),
    },
  ];
}

/** Every document a step list can write, deduplicated: what the run must be able to clean up. */
export function touchedPaths(steps) {
  const paths = steps.flatMap((step) =>
    step.op.type === 'commit' ? step.op.writes.map((write) => write.set?.path ?? write.delete) : [],
  );
  return [...new Set(paths)];
}

/** Throws unless the path is one this script creates: the guard between `--cleanup` and a delete. */
export function assertCleanable(path) {
  const [collection, id, ...rest] = path.split('/');
  if (rest.length > 0 || !CLEANABLE.has(collection) || !id?.startsWith(ID_PREFIX)) {
    throw new Error(`refusing to delete a document this script does not create: ${collection}/<id>`);
  }
}

// ---------------------------------------------------------------- //
// Verdicts
// ---------------------------------------------------------------- //

/**
 * `allow`, `deny`, `found`, `missing` or `error`. A 403 only counts as the rules denying when it
 * carries no machine-readable reason: a blocked API key or a disabled API is a 403 too, and
 * reading it as "denied" would pass a broken run.
 */
export function classify(op, { status, body }) {
  const error = body?.error ?? {};
  if (status === 403 && error.status === 'PERMISSION_DENIED' && !(error.details ?? []).some((d) => d.reason)) {
    return 'deny';
  }
  if (status === 200) return op.type === 'commit' ? 'allow' : 'found';
  if (status === 404 && op.type === 'get') return 'missing';
  return 'error';
}

/** `{ ok, detail }`: the step's expectation against what came back. */
export function evaluate(step, response) {
  const outcome = classify(step.op, response);
  if (outcome !== step.expect) {
    return { ok: false, detail: `expected ${step.expect}, got ${describeOutcome(outcome, response)}` };
  }
  if (outcome === 'found' && step.check) {
    const problem = step.check(decodeFields(response.body?.fields ?? {}));
    if (problem) return { ok: false, detail: problem };
  }
  return { ok: true, detail: outcome };
}

function describeOutcome(outcome, { status, body }) {
  if (outcome !== 'error') return outcome;
  const error = body?.error ?? {};
  return `HTTP ${status}${error.status ? ` ${error.status}` : ''}${error.message ? `: ${error.message}` : ''}`;
}

/** Strips the project and the key from anything that is about to be printed. */
export function redact(text, secrets) {
  return secrets.filter(Boolean).reduce((out, secret) => out.replaceAll(secret, '<redacted>'), text);
}

/** What to try when the very first write is refused: that is the stale-deployment signature. */
export const STALE_DEPLOY_HINT =
  'The first write is the one every new user makes. If it is refused, the deployed rules are older than the code: ' +
  'follow the deploy order in firebase/README.md.';

/** One output line per step, so a run reads as a list. */
export function formatLine(position, step, verdict) {
  const tag = verdict.ok ? 'ok  ' : 'FAIL';
  return `${tag} ${String(position).padStart(2, '0')} ${step.name} (${verdict.detail})`;
}
