// Mirrors `TaxonomyPurpose`; the app reads a purpose it does not know as none, which would
// quietly put a structural vocabulary back among the user's preferences.
export const PURPOSES = new Set(['UNITS', 'STORAGE_LOCATIONS', 'RECIPE_CLASSIFICATION']);

/** The ids of taxonomies declaring a purpose the client has no name for. */
export function unknownPurposes(taxonomies) {
  return Object.entries(taxonomies)
    .filter(([, { purpose }]) => purpose !== undefined && !PURPOSES.has(purpose))
    .map(([id]) => id);
}

/**
 * The `taxonomy/term` of every term whose `pluralLabels` is not a map of non-blank strings or
 * names a language its `labels` lack, which would show a singular and a plural in two tongues.
 */
export function invalidPluralLabels(taxonomies) {
  return Object.entries(taxonomies).flatMap(([taxonomyId, { terms }]) =>
    Object.entries(terms ?? {})
      .filter(([, { labels = {}, pluralLabels }]) => {
        if (pluralLabels === undefined) return false;
        if (pluralLabels === null || typeof pluralLabels !== 'object' || Array.isArray(pluralLabels)) return true;
        return Object.entries(pluralLabels).some(
          ([tag, word]) => typeof word !== 'string' || word.trim() === '' || !(tag in labels),
        );
      })
      .map(([termId]) => `${taxonomyId}/${termId}`),
  );
}
