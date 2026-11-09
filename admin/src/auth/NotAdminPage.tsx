interface NotAdminPageProps {
    email: string | null;
    onSignOut: () => void;
}

export function NotAdminPage({ email, onSignOut }: NotAdminPageProps) {
    return (
        <main className="centered">
            <h1>Not an admin</h1>
            <p>
                {email ?? 'This account'} has no admin access. Ask for it with{' '}
                <code>tools/grant-admin.mjs</code>, then sign in again.
            </p>
            <button type="button" onClick={onSignOut}>
                Sign out
            </button>
        </main>
    );
}
