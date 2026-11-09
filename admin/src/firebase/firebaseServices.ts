import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth } from 'firebase/auth';
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore';
import { connectFunctionsEmulator, getFunctions } from 'firebase/functions';
import { connectStorageEmulator, getStorage } from 'firebase/storage';
import { readFirebaseConfig } from './firebaseConfig';

const config = readFirebaseConfig(import.meta.env);
const app = initializeApp(config.options);

/** One instance of each SDK for the whole admin; nothing else calls initializeApp. */
export const firebaseServices = {
    auth: getAuth(app),
    firestore: getFirestore(app),
    storage: getStorage(app),
    functions: getFunctions(app, config.functionsRegion),
};

// Ports from firebase.json › emulators.
if (config.useEmulators) {
    connectAuthEmulator(firebaseServices.auth, 'http://127.0.0.1:9099', { disableWarnings: true });
    connectFirestoreEmulator(firebaseServices.firestore, '127.0.0.1', 8080);
    connectStorageEmulator(firebaseServices.storage, '127.0.0.1', 9199);
    connectFunctionsEmulator(firebaseServices.functions, '127.0.0.1', 5001);
}
