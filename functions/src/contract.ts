/**
 * The wire contract, mirrored from `docs/agent-contract.md`. The Kotlin DTOs in
 * `shared/data/remote/agent/dto` are the other half; these two files change together or the
 * feature breaks in the field.
 *
 * Nothing here trusts the caller. The client is our own app, but a callable is a public
 * endpoint and App Check is a lock, not a proof of what came through it.
 */

export const SCHEMA_VERSION = 1;

/** Hard ceilings on what a request may carry, so one caller cannot make a call cost anything. */
const MAX_PANTRY = 200;
const MAX_CONSTRAINTS = 100;
const MAX_LANGUAGE_TAGS = 5;
const MAX_RESULTS = 10;
const MAX_ID_LENGTH = 200;

export interface TermRef {
  taxonomy: string;
  term: string;
}

export interface Constraint extends TermRef {
  strength: 'PREFER' | 'AVOID' | 'EXCLUDE';
}

export interface PantryEntry {
  ingredientId: string;
  amount: number;
  unitTaxonomy: string | null;
  unitTerm: string | null;
  expiringSoon: boolean;
}

export interface SuggestRequest {
  schemaVersion: number;
  requestId: string;
  capability: string;
  languageTags: string[];
  servings: number;
  options: { maxResults: number; maxMinutes: number | null; useOnlyPantry: boolean };
  constraints: Constraint[];
  preferences: TermRef[];
  avoidedIngredients: string[];
  pantry: PantryEntry[];
}

export class BadRequest extends Error {}

/**
 * Parses and clamps. It throws rather than repairing silently: a request this function cannot
 * read is a client that will not understand the answer either.
 */
export function parseRequest(raw: unknown): SuggestRequest {
  const body = asObject(raw, 'request');
  if (body.schemaVersion !== SCHEMA_VERSION) {
    throw new BadRequest(`unsupported schemaVersion, this function speaks ${SCHEMA_VERSION}`);
  }
  if (body.capability !== 'SUGGEST_FROM_PANTRY') {
    throw new BadRequest('unsupported capability');
  }

  const options = asObject(body.options, 'options');
  return {
    schemaVersion: SCHEMA_VERSION,
    requestId: identifier(body.requestId, 'requestId'),
    capability: 'SUGGEST_FROM_PANTRY',
    languageTags: list(body.languageTags, MAX_LANGUAGE_TAGS).map((tag) => identifier(tag, 'languageTag')),
    servings: clampInt(body.servings, 1, 20, 'servings'),
    options: {
      maxResults: clampInt(options.maxResults, 1, MAX_RESULTS, 'maxResults'),
      maxMinutes: options.maxMinutes == null ? null : clampInt(options.maxMinutes, 1, 24 * 60, 'maxMinutes'),
      useOnlyPantry: options.useOnlyPantry === true,
    },
    constraints: list(body.constraints, MAX_CONSTRAINTS).map(constraint),
    preferences: list(body.preferences, MAX_CONSTRAINTS).map(termRef),
    avoidedIngredients: list(body.avoidedIngredients, MAX_CONSTRAINTS).map((id) =>
      identifier(id, 'avoidedIngredient'),
    ),
    pantry: list(body.pantry, MAX_PANTRY).map(pantryEntry),
  };
}

function constraint(raw: unknown): Constraint {
  const { taxonomy, term } = termRef(raw);
  const strength = asObject(raw, 'constraint').strength;
  if (strength !== 'PREFER' && strength !== 'AVOID' && strength !== 'EXCLUDE') {
    throw new BadRequest('unknown constraint strength');
  }
  return { taxonomy, term, strength };
}

function termRef(raw: unknown): TermRef {
  const value = asObject(raw, 'termRef');
  return { taxonomy: identifier(value.taxonomy, 'taxonomy'), term: identifier(value.term, 'term') };
}

function pantryEntry(raw: unknown): PantryEntry {
  const value = asObject(raw, 'pantryEntry');
  const unitTaxonomy = value.unitTaxonomy == null ? null : identifier(value.unitTaxonomy, 'unitTaxonomy');
  const unitTerm = value.unitTerm == null ? null : identifier(value.unitTerm, 'unitTerm');
  return {
    ingredientId: identifier(value.ingredientId, 'ingredientId'),
    amount: typeof value.amount === 'number' && Number.isFinite(value.amount) ? value.amount : 0,
    // Half a unit reference is no unit reference: it would name a term against nothing.
    unitTaxonomy: unitTerm == null ? null : unitTaxonomy,
    unitTerm: unitTaxonomy == null ? null : unitTerm,
    expiringSoon: value.expiringSoon === true,
  };
}

function asObject(raw: unknown, field: string): Record<string, unknown> {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new BadRequest(`${field} must be an object`);
  }
  return raw as Record<string, unknown>;
}

function list(raw: unknown, max: number): unknown[] {
  if (raw == null) return [];
  if (!Array.isArray(raw)) throw new BadRequest('expected a list');
  return raw.slice(0, max);
}

/**
 * Identifiers only. The alphabet is the point of the whole design: this is the boundary the
 * client's prose cannot cross, so anything that is not a plain id is refused rather than
 * cleaned up and passed to a model.
 */
function identifier(raw: unknown, field: string): string {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > MAX_ID_LENGTH) {
    throw new BadRequest(`${field} must be a short identifier`);
  }
  if (!/^[A-Za-z0-9._:-]+$/.test(raw)) {
    throw new BadRequest(`${field} must not contain free text`);
  }
  return raw;
}

function clampInt(raw: unknown, min: number, max: number, field: string): number {
  if (typeof raw !== 'number' || !Number.isFinite(raw)) throw new BadRequest(`${field} must be a number`);
  return Math.min(max, Math.max(min, Math.trunc(raw)));
}
