// The pure half of backfill-ingredient-requests.mjs: which queue documents a scan of the items
// implies. Normalisation is the function's own, so the trigger and the backfill agree on every key.
import { REQUESTS, displayText, freeTextKey, requestId } from '../../functions/src/ingredientRequests.ts';

export { REQUESTS };

const ITEM_PATH = /^kitchens\/[^/]+\/(pantry\/[^/]+|shoppingLists\/[^/]+\/items\/[^/]+)$/;

/** A pantry or shopping item: the `items` collection group could also match other parents. */
export function isFreeTextItemPath(path) {
  return ITEM_PATH.test(path);
}

export class UsageError extends Error {}

export function parseBackfillArgs(argv, env = {}) {
  const args = { project: env.KITCHENAI_PROJECT ?? null, write: false, help: false };
  for (let i = 0; i < argv.length; i += 1) {
    const flag = argv[i];
    if (flag === '--write') args.write = true;
    else if (flag === '--help' || flag === '-h') args.help = true;
    else if (flag === '--project' && argv[i + 1]) args.project = argv[(i += 1)];
    else throw new UsageError(`unknown argument ${flag}`);
  }
  if (!args.help && !args.project) throw new UsageError('--project is required');
  return args;
}

/**
 * `items` are `{ path, freeText, updatedAtMillis }`; `existing` maps request ids to their documents.
 * Returns the documents to write. `activeCount` is recounted from scratch; `count` never goes down;
 * status, ingredient and first sighting are kept. Running it twice writes nothing the second time.
 */
export function planBackfill(items, existing, nowMillis) {
  const groups = new Map();
  const ordered = [...items].sort(
    (a, b) => (a.updatedAtMillis ?? 0) - (b.updatedAtMillis ?? 0) || a.path.localeCompare(b.path),
  );
  for (const item of ordered) {
    const text = displayText(item.freeText);
    if (text === null) continue;
    const id = requestId(freeTextKey(text));
    const seen = typeof item.updatedAtMillis === 'number' ? item.updatedAtMillis : nowMillis;
    const group = groups.get(id);
    if (group === undefined) groups.set(id, { text, items: 1, first: seen, last: seen });
    else Object.assign(group, { items: group.items + 1, first: Math.min(group.first, seen), last: Math.max(group.last, seen) });
  }

  const writes = new Map();
  for (const id of new Set([...groups.keys(), ...existing.keys()])) {
    const group = groups.get(id);
    const current = existing.get(id);
    const next =
      current === undefined
        ? {
            text: group.text,
            count: group.items,
            activeCount: group.items,
            firstSeenMillis: group.first,
            lastSeenMillis: group.last,
            status: 'open',
          }
        : {
            ...current,
            count: Math.max(numberOr(current.count), group?.items ?? 0),
            activeCount: group?.items ?? 0,
          };
    if (current === undefined || !sameDocument(current, next)) writes.set(id, next);
  }
  return writes;
}

function numberOr(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function sameDocument(a, b) {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  return [...keys].every((key) => a[key] === b[key]);
}
