import { describe, expect, it } from 'vitest';
import { isAdminClaims } from './isAdminClaims';

describe('isAdminClaims', () => {
    it('accepts admin set to true and nothing else', () => {
        expect(isAdminClaims({ admin: true })).toBe(true);
        expect(isAdminClaims({ admin: 'true' })).toBe(false);
        expect(isAdminClaims({ admin: 1 })).toBe(false);
        expect(isAdminClaims({})).toBe(false);
    });
});
