import type { FirebaseOptions } from 'firebase/app';

export interface AdminFirebaseConfig {
    options: FirebaseOptions;
    functionsRegion: string;
    useEmulators: boolean;
}

const REQUIRED = [
    'VITE_FIREBASE_API_KEY',
    'VITE_FIREBASE_AUTH_DOMAIN',
    'VITE_FIREBASE_PROJECT_ID',
    'VITE_FIREBASE_STORAGE_BUCKET',
    'VITE_FIREBASE_APP_ID',
] as const;

/** Reads the web config from the build environment; a missing value fails loudly instead of at the first call. */
export function readFirebaseConfig(env: Record<string, string | undefined>): AdminFirebaseConfig {
    const missing = REQUIRED.filter((name) => !env[name]?.trim());
    if (missing.length > 0) {
        throw new Error(
            `Missing ${missing.join(', ')}: copy admin/.env.example to admin/.env.local and fill it in.`,
        );
    }
    return {
        options: {
            apiKey: env.VITE_FIREBASE_API_KEY,
            authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
            projectId: env.VITE_FIREBASE_PROJECT_ID,
            storageBucket: env.VITE_FIREBASE_STORAGE_BUCKET,
            appId: env.VITE_FIREBASE_APP_ID,
        },
        functionsRegion: env.VITE_FIREBASE_FUNCTIONS_REGION?.trim() || 'europe-southwest1',
        useEmulators: env.VITE_USE_EMULATORS === 'true',
    };
}
