import { BadRequest, asObject, identifier } from './contract.ts';

/**
 * The payload of `writeCatalogue`, validated against the shapes `firebase/seed/*.json` and the
 * Kotlin `IngredientDto` / `RecipeDto` read. Unknown fields are refused, never dropped: a field
 * the client cannot decode is a document it cannot show.
 */
export type CatalogueKind = 'ingredient' | 'recipe';

export interface CatalogueEntry {
  kind: CatalogueKind;
  id: string;
  document: Record<string, unknown>;
}

/** The only collections this function can ever write, so a payload cannot name another one. */
export const COLLECTIONS: Record<CatalogueKind, string> = {
  ingredient: 'ingredients',
  recipe: 'recipes',
};

// Same ceiling as `isId` in firestore.rules: a longer id could never be referenced from a pantry.
const MAX_ID_LENGTH = 128;
const MAX_LABEL = 200;
const MAX_LANGUAGES = 20;
const MAX_TAXONOMIES = 20;
const MAX_TERMS_PER_TAXONOMY = 50;
const MAX_TITLE = 200;
const MAX_SUMMARY = 2000;
const MAX_LINES = 100;
const MAX_STEPS = 100;
const MAX_STEP = 2000;
const MAX_RECIPE_TAGS = 50;
const MAX_AMOUNT = 1_000_000;
const MAX_SERVINGS = 100;
const MAX_MINUTES = 24 * 60;

export function parseCatalogueEntry(raw: unknown): CatalogueEntry {
  const body = asObject(raw, 'request');
  onlyKeys(body, ['kind', 'id', 'data'], 'request');
  const id = documentId(body.id);
  const data = asObject(body.data, 'data');
  switch (body.kind) {
    case 'ingredient':
      return { kind: 'ingredient', id, document: ingredient(data) };
    case 'recipe':
      return { kind: 'recipe', id, document: recipe(data) };
    default:
      throw new BadRequest('kind must be ingredient or recipe');
  }
}

function ingredient(data: Record<string, unknown>): Record<string, unknown> {
  onlyKeys(data, ['labels', 'defaultUnitTaxonomy', 'defaultUnitTerm', 'tags', 'purchasedWhole'], 'ingredient');
  const document: Record<string, unknown> = { labels: labels(data.labels) };
  const unit = optionalUnit(data.defaultUnitTaxonomy, data.defaultUnitTerm, 'defaultUnit');
  if (unit) {
    document.defaultUnitTaxonomy = unit.taxonomy;
    document.defaultUnitTerm = unit.term;
  }
  document.tags = groupedTags(data.tags);
  if (data.purchasedWhole !== undefined) document.purchasedWhole = bool(data.purchasedWhole, 'purchasedWhole');
  return document;
}

function recipe(data: Record<string, unknown>): Record<string, unknown> {
  onlyKeys(data, ['title', 'summary', 'servings', 'totalMinutes', 'ingredients', 'steps', 'tags'], 'recipe');
  const document: Record<string, unknown> = { title: text(data.title, MAX_TITLE, 'title') };
  if (data.summary != null) document.summary = text(data.summary, MAX_SUMMARY, 'summary');
  document.servings = integer(data.servings, 1, MAX_SERVINGS, 'servings');
  if (data.totalMinutes != null) document.totalMinutes = integer(data.totalMinutes, 1, MAX_MINUTES, 'totalMinutes');
  document.ingredients = nonEmptyList(data.ingredients, MAX_LINES, 'ingredients').map(recipeLine);
  document.steps = nonEmptyList(data.steps, MAX_STEPS, 'steps').map((step) => text(step, MAX_STEP, 'step'));
  document.tags = optionalList(data.tags, MAX_RECIPE_TAGS, 'tags').map(termRef);
  // Provenance is the server's to state: an admin write is curated content, never `agent`.
  document.source = { type: 'catalogue' };
  return document;
}

function recipeLine(raw: unknown): Record<string, unknown> {
  const line = asObject(raw, 'ingredient line');
  onlyKeys(line, ['ingredientId', 'freeText', 'amount', 'unitTaxonomy', 'unitTerm', 'optional'], 'ingredient line');
  if ((line.ingredientId == null) === (line.freeText == null)) {
    throw new BadRequest('exactly one of ingredientId or freeText must be set');
  }
  const out: Record<string, unknown> = {};
  if (line.ingredientId != null) out.ingredientId = id(line.ingredientId, 'ingredientId');
  if (line.freeText != null) out.freeText = text(line.freeText, MAX_LABEL, 'freeText');
  const unit = optionalUnit(line.unitTaxonomy, line.unitTerm, 'unit');
  if (line.amount != null) {
    const amount = line.amount;
    if (typeof amount !== 'number' || !Number.isFinite(amount) || amount <= 0 || amount > MAX_AMOUNT) {
      throw new BadRequest('amount must be a positive number');
    }
    out.amount = amount;
  } else if (unit) {
    // A unit with no amount is corruption on the Kotlin side, so it is refused here too.
    throw new BadRequest('a unit needs an amount');
  }
  if (unit) {
    out.unitTaxonomy = unit.taxonomy;
    out.unitTerm = unit.term;
  }
  if (line.optional !== undefined) out.optional = bool(line.optional, 'optional');
  return out;
}

function termRef(raw: unknown): { taxonomy: string; term: string } {
  const ref = asObject(raw, 'tag');
  onlyKeys(ref, ['taxonomy', 'term'], 'tag');
  return { taxonomy: id(ref.taxonomy, 'taxonomy'), term: id(ref.term, 'term') };
}

/** Both halves or neither, like `termRefOrNull` on the Kotlin side. */
function optionalUnit(taxonomy: unknown, term: unknown, field: string): { taxonomy: string; term: string } | null {
  if (taxonomy == null && term == null) return null;
  if (taxonomy == null || term == null) throw new BadRequest(`${field} needs both a taxonomy and a term`);
  return { taxonomy: id(taxonomy, 'taxonomy'), term: id(term, 'term') };
}

function labels(raw: unknown): Record<string, string> {
  const map = asObject(raw, 'labels');
  const entries = Object.entries(map);
  if (entries.length === 0 || entries.length > MAX_LANGUAGES) throw new BadRequest('labels needs 1 to 20 languages');
  const out: Record<string, string> = {};
  for (const [tag, value] of entries) {
    if (!/^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$/.test(tag)) throw new BadRequest('label key must be a language tag');
    out[tag] = text(value, MAX_LABEL, 'label');
  }
  return out;
}

function groupedTags(raw: unknown): Record<string, string[]> {
  if (raw == null) return {};
  const entries = Object.entries(asObject(raw, 'tags'));
  if (entries.length > MAX_TAXONOMIES) throw new BadRequest('too many tag taxonomies');
  const out: Record<string, string[]> = {};
  for (const [taxonomy, terms] of entries) {
    out[id(taxonomy, 'taxonomy')] = optionalList(terms, MAX_TERMS_PER_TAXONOMY, 'tags').map((term) => id(term, 'term'));
  }
  return out;
}

/** The catalogue id alphabet, capped at what the rules accept, minus ids Firestore reserves. */
function documentId(raw: unknown): string {
  const value = id(raw, 'id');
  if (value === '.' || value === '..' || value.startsWith('__')) throw new BadRequest('id is reserved');
  return value;
}

function id(raw: unknown, field: string): string {
  const value = identifier(raw, field);
  if (value.length > MAX_ID_LENGTH) throw new BadRequest(`${field} must be a short identifier`);
  return value;
}

function text(raw: unknown, max: number, field: string): string {
  if (typeof raw !== 'string' || raw.trim().length === 0 || raw.length > max) {
    throw new BadRequest(`${field} must be non-empty text of at most ${max} characters`);
  }
  return raw;
}

function integer(raw: unknown, min: number, max: number, field: string): number {
  if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < min || raw > max) {
    throw new BadRequest(`${field} must be an integer from ${min} to ${max}`);
  }
  return raw;
}

function bool(raw: unknown, field: string): boolean {
  if (typeof raw !== 'boolean') throw new BadRequest(`${field} must be true or false`);
  return raw;
}

function optionalList(raw: unknown, max: number, field: string): unknown[] {
  if (raw == null) return [];
  if (!Array.isArray(raw) || raw.length > max) throw new BadRequest(`${field} must be a list of at most ${max}`);
  return raw;
}

function nonEmptyList(raw: unknown, max: number, field: string): unknown[] {
  const items = optionalList(raw, max, field);
  if (items.length === 0) throw new BadRequest(`${field} must not be empty`);
  return items;
}

function onlyKeys(value: Record<string, unknown>, allowed: string[], field: string): void {
  if (Object.keys(value).some((key) => !allowed.includes(key))) throw new BadRequest(`${field} has an unknown field`);
}
