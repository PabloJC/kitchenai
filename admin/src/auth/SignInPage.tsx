export function SignInPage({ onSignIn }: { onSignIn: () => void }) {
    return (
        <main className="centered">
            <h1>KitchenAI admin</h1>
            <button type="button" onClick={onSignIn}>
                Sign in with Google
            </button>
        </main>
    );
}
