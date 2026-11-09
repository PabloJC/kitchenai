import type { AdminSection } from '../layout/adminSections';

export function PlaceholderPage({ section }: { section: AdminSection }) {
    return (
        <section>
            <h1>{section.title}</h1>
            <p>Not built yet (#{section.issue}).</p>
        </section>
    );
}
