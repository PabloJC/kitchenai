import { onIdTokenChanged } from 'firebase/auth';
import { useEffect, useState } from 'react';
import { firebaseServices } from '../firebase/firebaseServices';
import type { AdminSession } from './AdminSession';
import { isAdminClaims } from './isAdminClaims';

/** Follows the ID token, not just the user, so a claim granted or revoked shows on the next refresh. */
export function useAdminSession(): AdminSession {
    const [session, setSession] = useState<AdminSession>({ status: 'loading' });

    useEffect(
        () =>
            onIdTokenChanged(firebaseServices.auth, async (user) => {
                if (!user) {
                    setSession({ status: 'signedOut' });
                    return;
                }
                const { claims } = await user.getIdTokenResult();
                setSession(
                    isAdminClaims(claims)
                        ? { status: 'admin', user }
                        : { status: 'notAdmin', email: user.email },
                );
            }),
        [],
    );

    return session;
}
