// The pure parts of tools/catalogue.mjs and tools/grant-admin.mjs: no network, no credentials,
// so the argument handling and the dry-run verdict can be tested.

import { parseArgs } from 'node:util';

export class UsageError extends Error {}

const DEFAULT_REGION = 'europe-southwest1';

const COMMON = {
  project: { type: 'string' },
  help: { type: 'boolean', default: false },
};

function parse(argv, options) {
  try {
    return parseArgs({ args: argv, options, allowPositionals: true });
  } catch (failure) {
    throw new UsageError(failure.message);
  }
}

/** Flags win over the environment; nothing here has a default that names a project. */
export function parseCatalogueArgs(argv, env = {}) {
  const { values, positionals } = parse(argv, {
    ...COMMON,
    'dry-run': { type: 'boolean', default: false },
    region: { type: 'string' },
    uid: { type: 'string' },
    'api-key': { type: 'string' },
  });
  if (positionals.length > 1) throw new UsageError('at most one payload file; omit it or pass - to read stdin');
  const args = {
    help: values.help,
    dryRun: values['dry-run'],
    file: positionals[0] ?? '-',
    project: values.project ?? env.KITCHENAI_PROJECT ?? null,
    region: values.region ?? env.KITCHENAI_REGION ?? DEFAULT_REGION,
    uid: values.uid ?? env.KITCHENAI_ADMIN_UID ?? null,
    apiKey: values['api-key'] ?? env.KITCHENAI_API_KEY ?? null,
    // Environment only: a token on the command line ends up in shell history and `ps`.
    idToken: env.KITCHENAI_ID_TOKEN ?? null,
  };
  if (args.help || args.dryRun) return args;
  // No default on purpose: the wrong project here is a silent write to somebody else's data.
  if (!args.project) throw new UsageError('--project <projectId> (or KITCHENAI_PROJECT) is required');
  if (!args.idToken && !(args.uid && args.apiKey)) {
    throw new UsageError(
      'sign-in needs KITCHENAI_ID_TOKEN, or --uid and --api-key (KITCHENAI_ADMIN_UID, KITCHENAI_API_KEY)',
    );
  }
  return args;
}

export function parseGrantArgs(argv, env = {}) {
  const { values, positionals } = parse(argv, {
    ...COMMON,
    uid: { type: 'string' },
    revoke: { type: 'boolean', default: false },
  });
  if (positionals.length > 0) throw new UsageError('unexpected argument');
  const args = {
    help: values.help,
    revoke: values.revoke,
    project: values.project ?? env.KITCHENAI_PROJECT ?? null,
    uid: values.uid ?? env.KITCHENAI_ADMIN_UID ?? null,
  };
  if (args.help) return args;
  if (!args.project) throw new UsageError('--project <projectId> (or KITCHENAI_PROJECT) is required');
  if (!args.uid) throw new UsageError('--uid <uid> (or KITCHENAI_ADMIN_UID) is required');
  return args;
}

export function parsePayload(text) {
  if (text.trim().length === 0) throw new UsageError('the payload is empty');
  try {
    return JSON.parse(text);
  } catch {
    throw new UsageError('the payload is not valid JSON');
  }
}

/**
 * The function's own validator, imported rather than copied so the two cannot drift. Node runs
 * the `.ts` source directly (type stripping), which needs Node 22.18+ or the flag in the message.
 */
async function loadValidator() {
  try {
    const [entry, contract] = await Promise.all([
      import('../../functions/src/catalogueEntry.ts'),
      import('../../functions/src/contract.ts'),
    ]);
    return { parseCatalogueEntry: entry.parseCatalogueEntry, BadRequest: contract.BadRequest };
  } catch (failure) {
    if (failure?.code === 'ERR_UNKNOWN_FILE_EXTENSION') {
      throw new UsageError('this needs Node 22.18+, or run with: node --experimental-strip-types <script>');
    }
    throw failure;
  }
}

/** `{ ok: true, kind, id }`, or `{ ok: false, message }` with the message the callable would send. */
export async function validatePayload(raw) {
  const { parseCatalogueEntry, BadRequest } = await loadValidator();
  try {
    const entry = parseCatalogueEntry(raw);
    return { ok: true, kind: entry.kind, id: entry.id };
  } catch (failure) {
    if (failure instanceof BadRequest) return { ok: false, message: failure.message };
    throw failure;
  }
}

export function callableUrl(project, region) {
  return `https://${region}-${project}.cloudfunctions.net/writeCatalogue`;
}

/** What happened to the document; the function replaces an existing id, so say so. */
export function describeResult({ kind, id, created }) {
  return created ? `created ${kind} "${id}"` : `replaced the existing ${kind} "${id}" with this payload`;
}

/** Maps a callable failure to something actionable; only the function's own message is echoed. */
export function describeFailure(status, body) {
  const error = body?.error ?? {};
  switch (error.status) {
    case 'UNAUTHENTICATED':
      return 'not signed in: the ID token is missing, expired or rejected. Get a fresh one.';
    case 'PERMISSION_DENIED':
      return 'this account has no admin claim, or its token predates it. Run tools/grant-admin.mjs, then sign in again.';
    case 'INVALID_ARGUMENT':
      return `the function rejected the payload: ${error.message ?? 'no detail'}`;
    case 'NOT_FOUND':
      return 'function not found: check --project and --region, and that writeCatalogue is deployed.';
    default:
      return `HTTP ${status}${error.status ? ` ${error.status}` : ''}${error.message ? `: ${error.message}` : ''}`;
  }
}
