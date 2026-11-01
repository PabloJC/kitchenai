/** The token every page carries until the owner sets a real contact address. */
export const CONTACT_PLACEHOLDER = 'CONTACT_EMAIL_PLACEHOLDER';

/** One line per page that still holds the placeholder; empty when the pages are ready to publish. */
export function findPlaceholders(pages) {
  return Object.entries(pages)
    .filter(([, html]) => html.includes(CONTACT_PLACEHOLDER))
    .map(([name]) => `${name}: still has ${CONTACT_PLACEHOLDER}`);
}
