"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "@/lib/session";
import { ApiStatusBadge } from "./api-status-badge";

export function AppShell({ children }: { children: React.ReactNode }) {
  const { state, signOut } = useSession();
  const router = useRouter();

  async function handleSignOut() {
    await signOut();
    // Protected pages also redirect here once the session is gone: one destination, no race.
    router.replace("/connexion");
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link className="brand" href={state.status === "in" ? "/domaines" : "/"}>
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
            <path d="M12 2.5 14.6 8h-5.2L12 2.5Z" fill="currentColor" />
            <path d="M9.6 9.5h4.8l1.9 12H7.7l1.9-12Z" fill="currentColor" opacity="0.55" />
            <path d="M3 6.2 8 7.6M21 6.2l-5 1.4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" fill="none" />
          </svg>
          Vigie
        </Link>
        <nav className="topbar-nav" aria-label="Compte">
          {state.status === "in" && (
            <>
              <span className="topbar-email">{state.user.email}</span>
              <button type="button" className="button button-quiet" onClick={handleSignOut}>
                Se déconnecter
              </button>
            </>
          )}
          {state.status === "out" && (
            <Link className="button button-quiet" href="/connexion">
              Se connecter
            </Link>
          )}
        </nav>
      </header>
      <main className="page">{children}</main>
      <footer className="footer">
        <ApiStatusBadge />
      </footer>
    </div>
  );
}
