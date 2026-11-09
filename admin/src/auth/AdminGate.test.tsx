import { render, screen } from '@testing-library/react';
import type { User } from 'firebase/auth';
import { describe, expect, it, vi } from 'vitest';
import { AdminGate } from './AdminGate';
import type { AdminSession } from './AdminSession';

const renderGate = (session: AdminSession) => {
    // Stands in for every admin page: anything that would read Firestore.
    const Protected = vi.fn(() => <p>protected</p>);
    render(
        <AdminGate session={session} onSignIn={() => {}} onSignOut={() => {}}>
            <Protected />
        </AdminGate>,
    );
    return Protected;
};

describe('AdminGate', () => {
    it('renders nothing protected for an account without the claim', () => {
        const protectedPage = renderGate({ status: 'notAdmin', email: null });
        expect(screen.getByRole('heading', { name: 'Not an admin' })).toBeInTheDocument();
        expect(protectedPage).not.toHaveBeenCalled();
    });

    it('asks a signed-out visitor to sign in', () => {
        const protectedPage = renderGate({ status: 'signedOut' });
        expect(screen.getByRole('button', { name: 'Sign in with Google' })).toBeInTheDocument();
        expect(protectedPage).not.toHaveBeenCalled();
    });

    it('renders nothing protected while the token is being read', () => {
        expect(renderGate({ status: 'loading' })).not.toHaveBeenCalled();
    });

    it('renders the admin pages for an admin', () => {
        const protectedPage = renderGate({ status: 'admin', user: {} as User });
        expect(screen.getByText('protected')).toBeInTheDocument();
        expect(protectedPage).toHaveBeenCalled();
    });
});
