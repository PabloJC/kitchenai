export interface AdminSection {
    path: string;
    title: string;
    /** The issue that fills the section; shown until it lands. */
    issue: number;
}

export const ADMIN_SECTIONS: readonly AdminSection[] = [
    { path: 'ingredients', title: 'Ingredients', issue: 403 },
    { path: 'recipes', title: 'Recipes', issue: 406 },
    { path: 'taxonomies', title: 'Taxonomies', issue: 407 },
    { path: 'free-text', title: 'Free-text queue', issue: 404 },
    { path: 'usage', title: 'Usage', issue: 409 },
];
