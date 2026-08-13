import type { Firestore } from 'firebase-admin/firestore';
import type { Constraint, PantryEntry, SuggestRequest, TermRef } from './contract.js';

/**
 * Turning identifiers into words, against the same documents the app reads. This is the half of
 * the design that keeps prose off the device: the client sends `units/gram`, and only here does
 * that become "g".
 */

interface Labelled {
  labels?: Record<string, string>;
  defaultLanguageTag?: string;
}

export interface Catalogue {
  ingredient(id: string): string | null;
  term(ref: TermRef): string | null;
}

/**
 * Read once per call and held for the call only. The catalogue is small and read-only, and a
 * cache that outlives the invocation would serve a vocabulary the user has already been shown
 * a newer version of.
 */
export async function loadCatalogue(db: Firestore, languageTags: string[]): Promise<Catalogue> {
  const [ingredients, taxonomies] = await Promise.all([
    db.collection('ingredients').get(),
    db.collection('taxonomies').get(),
  ]);

  const ingredientLabels = new Map<string, string | null>();
  for (const doc of ingredients.docs) {
    ingredientLabels.set(doc.id, resolve(doc.data() as Labelled, languageTags));
  }

  const termLabels = new Map<string, string | null>();
  await Promise.all(
    taxonomies.docs.map(async (taxonomy) => {
      const terms = await taxonomy.ref.collection('terms').get();
      for (const term of terms.docs) {
        termLabels.set(`${taxonomy.id}/${term.id}`, resolve(term.data() as Labelled, languageTags));
      }
    }),
  );

  return {
    ingredient: (id) => ingredientLabels.get(id) ?? null,
    term: (ref) => termLabels.get(`${ref.taxonomy}/${ref.term}`) ?? null,
  };
}

/**
 * The user's language order, then the document's own default, then any label at all. Returning
 * null rather than the identifier is deliberate: an unresolvable reference is dropped from the
 * request instead of sending a model a word that is really a database key.
 */
export function resolve(document: Labelled, languageTags: string[]): string | null {
  const labels = document.labels ?? {};
  for (const tag of languageTags) {
    const exact = labels[tag];
    if (exact) return exact;
    // "es-ES" should find "es"; a region is a preference, not a different vocabulary.
    const base = tag.split('-')[0];
    if (base) {
      const relaxed = labels[base];
      if (relaxed) return relaxed;
    }
  }
  const fallback = document.defaultLanguageTag ? labels[document.defaultLanguageTag] : undefined;
  return fallback ?? Object.values(labels)[0] ?? null;
}

/** The request as words, ready to be described to a model. Anything unresolvable is gone. */
export interface ReadableRequest {
  servings: number;
  maxResults: number;
  maxMinutes: number | null;
  useOnlyPantry: boolean;
  pantry: { name: string; amount: number; unit: string | null; expiringSoon: boolean }[];
  excluded: string[];
  avoided: string[];
  preferred: string[];
}

export function toReadable(request: SuggestRequest, catalogue: Catalogue): ReadableRequest {
  const named = (list: Constraint[], strength: Constraint['strength']) =>
    list.filter((it) => it.strength === strength).map((it) => catalogue.term(it)).filter(isText);

  return {
    servings: request.servings,
    maxResults: request.options.maxResults,
    maxMinutes: request.options.maxMinutes,
    useOnlyPantry: request.options.useOnlyPantry,
    pantry: request.pantry.map((entry) => holding(entry, catalogue)).filter((it) => it !== null),
    excluded: [
      ...named(request.constraints, 'EXCLUDE'),
      ...request.avoidedIngredients.map((id) => catalogue.ingredient(id)).filter(isText),
    ],
    avoided: named(request.constraints, 'AVOID'),
    preferred: [...named(request.constraints, 'PREFER'), ...request.preferences.map((it) => catalogue.term(it)).filter(isText)],
  };
}

function holding(entry: PantryEntry, catalogue: Catalogue): ReadableRequest['pantry'][number] | null {
  const name = catalogue.ingredient(entry.ingredientId);
  if (!name) return null;
  const unit =
    entry.unitTaxonomy && entry.unitTerm
      ? catalogue.term({ taxonomy: entry.unitTaxonomy, term: entry.unitTerm })
      : null;
  return { name, amount: entry.amount, unit, expiringSoon: entry.expiringSoon };
}

function isText(value: string | null): value is string {
  return value !== null && value.length > 0;
}
