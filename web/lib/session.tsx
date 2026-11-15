"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "./api";
import type { User } from "./types";

type SessionState = { status: "loading" } | { status: "in"; user: User } | { status: "out" };

interface Session {
  state: SessionState;
  signIn: (user: User) => void;
  signOut: () => Promise<void>;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    api
      .get<User>("/api/v1/me")
      .then((user) => !cancelled && setState({ status: "in", user }))
      .catch(() => !cancelled && setState({ status: "out" }));
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback((user: User) => setState({ status: "in", user }), []);

  const signOut = useCallback(async () => {
    // Even if the request fails, the interface must stop showing the account.
    await api.post("/api/v1/auth/logout").catch(() => undefined);
    setState({ status: "out" });
  }, []);

  const value = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error("useSession must be used inside SessionProvider");
  return session;
}

/**
 * For pages that need an account: sends visitors to the sign-in page.
 * This only improves navigation. Access control is enforced by the API,
 * which answers 401 to any request without a valid session.
 */
export function useRequireUser(): User | null {
  const { state } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "out") router.replace("/connexion");
  }, [state.status, router]);

  return state.status === "in" ? state.user : null;
}
