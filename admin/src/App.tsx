import { BrowserRouter } from 'react-router';
import { AdminGate } from './auth/AdminGate';
import { signInWithGoogle, signOutOfAdmin } from './auth/authActions';
import { useAdminSession } from './auth/useAdminSession';
import { AdminRoutes } from './AdminRoutes';

export function App() {
    const session = useAdminSession();
    const signIn = () => void signInWithGoogle();
    const signOut = () => void signOutOfAdmin();

    return (
        <AdminGate session={session} onSignIn={signIn} onSignOut={signOut}>
            {session.status === 'admin' && (
                <BrowserRouter>
                    <AdminRoutes email={session.user.email} onSignOut={signOut} />
                </BrowserRouter>
            )}
        </AdminGate>
    );
}
