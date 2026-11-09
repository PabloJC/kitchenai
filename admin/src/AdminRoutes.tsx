import { Navigate, Route, Routes } from 'react-router';
import { ADMIN_SECTIONS } from './layout/adminSections';
import { Shell } from './layout/Shell';
import { PlaceholderPage } from './pages/PlaceholderPage';

interface AdminRoutesProps {
    email: string | null;
    onSignOut: () => void;
}

export function AdminRoutes({ email, onSignOut }: AdminRoutesProps) {
    return (
        <Routes>
            <Route element={<Shell email={email} onSignOut={onSignOut} />}>
                <Route index element={<Navigate to={ADMIN_SECTIONS[0]?.path ?? '/'} replace />} />
                {ADMIN_SECTIONS.map((section) => (
                    <Route
                        key={section.path}
                        path={section.path}
                        element={<PlaceholderPage section={section} />}
                    />
                ))}
                <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
        </Routes>
    );
}
