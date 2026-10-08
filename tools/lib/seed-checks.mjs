// Mirrors `TaxonomyPurpose`; the app reads a purpose it does not know as none, which would
// quietly put a structural vocabulary back among the user's preferences.
export const PURPOSES = new Set(['UNITS', 'STORAGE_LOCATIONS', 'RECIPE_CLASSIFICATION']);

/** The ids of taxonomies declaring a purpose the client has no name for. */
export function unknownPurposes(taxonomies) {
  return Object.entries(taxonomies)
    .filter(([, { purpose }]) => purpose !== undefined && !PURPOSES.has(purpose))
    .map(([id]) => id);
}
