import type { ReactNode } from 'react';
import type { AdminSession } from './AdminSession';
import { NotAdminPage } from './NotAdminPage';
import { SignInPage } from './SignInPage';

interface AdminGateProps {
    session: AdminSession;
    onSignIn: () => void;
    onSignOut: () => void;
    children: ReactNode;
}

/** The UI gate. The rules are the security gate: this only keeps a non-admin from issuing reads at all. */
export function AdminGate({ session, onSignIn, onSignOut, children }: AdminGateProps) {
    switch (session.status) {
        case 'loading':
            return <p className="centered">Loading…</p>;
        case 'signedOut':
            return <SignInPage onSignIn={onSignIn} />;
        case 'notAdmin':
            return <NotAdminPage email={session.email} onSignOut={onSignOut} />;
        case 'admin':
            return <>{children}</>;
    }
}
