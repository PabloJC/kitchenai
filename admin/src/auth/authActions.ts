import { GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth';
import { firebaseServices } from '../firebase/firebaseServices';

export function signInWithGoogle(): Promise<unknown> {
    return signInWithPopup(firebaseServices.auth, new GoogleAuthProvider());
}

export function signOutOfAdmin(): Promise<void> {
    return signOut(firebaseServices.auth);
}
