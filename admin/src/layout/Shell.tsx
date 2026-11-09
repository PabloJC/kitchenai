import { NavLink, Outlet } from 'react-router';
import { ADMIN_SECTIONS } from './adminSections';

interface ShellProps {
    email: string | null;
    onSignOut: () => void;
}

export function Shell({ email, onSignOut }: ShellProps) {
    return (
        <div className="shell">
            <nav className="shell-nav">
                <strong>KitchenAI admin</strong>
                <ul>
                    {ADMIN_SECTIONS.map((section) => (
                        <li key={section.path}>
                            <NavLink to={section.path}>{section.title}</NavLink>
                        </li>
                    ))}
                </ul>
                <div className="shell-account">
                    <span>{email}</span>
                    <button type="button" onClick={onSignOut}>
                        Sign out
                    </button>
                </div>
            </nav>
            <main className="shell-content">
                <Outlet />
            </main>
        </div>
    );
}
