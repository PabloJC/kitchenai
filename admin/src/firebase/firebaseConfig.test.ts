import { describe, expect, it } from 'vitest';
import { readFirebaseConfig } from './firebaseConfig';

const complete = {
    VITE_FIREBASE_API_KEY: 'key',
    VITE_FIREBASE_AUTH_DOMAIN: 'domain',
    VITE_FIREBASE_PROJECT_ID: 'project',
    VITE_FIREBASE_STORAGE_BUCKET: 'bucket',
    VITE_FIREBASE_APP_ID: 'app',
};

describe('readFirebaseConfig', () => {
    it('names every missing variable', () => {
        expect(() =>
            readFirebaseConfig({ ...complete, VITE_FIREBASE_API_KEY: ' ', VITE_FIREBASE_APP_ID: undefined }),
        ).toThrow('Missing VITE_FIREBASE_API_KEY, VITE_FIREBASE_APP_ID');
    });

    it('defaults the region and keeps the emulators off unless asked', () => {
        const config = readFirebaseConfig(complete);
        expect(config.functionsRegion).toBe('europe-southwest1');
        expect(config.useEmulators).toBe(false);
        expect(config.options.projectId).toBe('project');
    });

    it('turns the emulators on only for the exact string true', () => {
        expect(readFirebaseConfig({ ...complete, VITE_USE_EMULATORS: 'true' }).useEmulators).toBe(true);
        expect(readFirebaseConfig({ ...complete, VITE_USE_EMULATORS: '1' }).useEmulators).toBe(false);
    });
});
