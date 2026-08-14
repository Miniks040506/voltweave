"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/components/auth-provider";
import { AppRole } from "@/lib/keycloak";

const destinations: { href: string; label: string; role: AppRole }[] = [
  { href: "/customer", label: "My energy", role: "CUSTOMER" },
  { href: "/operator", label: "VPP operations", role: "VPP_OPERATOR" },
  { href: "/admin", label: "Platform", role: "ADMIN" },
];

export function AppShell({
  role,
  title,
  description,
  children,
}: {
  role: AppRole;
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  const auth = useAuth();
  const pathname = usePathname();

  if (!auth.ready) return <CenteredMessage text="Loading your workspace..." />;
  if (auth.error) return <CenteredMessage text={auth.error} error />;
  if (!auth.authenticated) {
    return (
      <main className="centered-state">
        <p>Your session is not active.</p>
        <button className="primary-button" onClick={() => void auth.login()}>Sign in</button>
      </main>
    );
  }
  if (!auth.roles.includes(role) && !auth.roles.includes("ADMIN")) {
    return <CenteredMessage text="Your account does not have access to this workspace." error />;
  }

  const visibleDestinations = destinations.filter((item) => auth.roles.includes(item.role));

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <Link className="brand" href="/">VoltWeave</Link>
        <nav className="workspace-nav" aria-label="Workspaces">
          {visibleDestinations.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={pathname === item.href ? "page" : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="account-block">
          <strong>{auth.name}</strong>
          <span>{auth.roles.join(" · ")}</span>
          <button className="text-button" onClick={() => void auth.logout()}>Sign out</button>
        </div>
      </aside>
      <main className="workspace">
        <header className="workspace-header">
          <p className="eyebrow">LIVE SANDBOX</p>
          <h1>{title}</h1>
          <p>{description}</p>
        </header>
        {children}
      </main>
    </div>
  );
}

function CenteredMessage({ text, error = false }: { text: string; error?: boolean }) {
  return (
    <main className="centered-state" aria-live="polite">
      <p className={error ? "error-message" : "status"}>{text}</p>
      <Link className="text-link" href="/">Return home</Link>
    </main>
  );
}
