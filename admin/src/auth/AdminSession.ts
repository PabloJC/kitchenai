import type { User } from 'firebase/auth';

/** Where the signed-in account stands; only `admin` may render anything that reads Firestore. */
export type AdminSession =
    | { status: 'loading' }
    | { status: 'signedOut' }
    | { status: 'notAdmin'; email: string | null }
    | { status: 'admin'; user: User };
