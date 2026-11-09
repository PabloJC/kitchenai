/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
    plugins: [react()],
    build: {
        // The Firestore SDK alone is about 500 kB minified; the admin is not a public page.
        chunkSizeWarningLimit: 700,
        rollupOptions: {
            // The SDKs change far less often than the admin's own code, so they cache apart from it.
            output: {
                manualChunks: {
                    firebase: [
                        'firebase/app',
                        'firebase/auth',
                        'firebase/firestore',
                        'firebase/storage',
                        'firebase/functions',
                    ],
                    react: ['react', 'react-dom', 'react-router'],
                },
            },
        },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/setupTests.ts'],
    },
});
