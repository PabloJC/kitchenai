/** `true` exactly, as `isAdmin()` in the rules: a claim set to "true" or 1 is not an admin. */
export function isAdminClaims(claims: Record<string, unknown>): boolean {
    return claims.admin === true;
}
