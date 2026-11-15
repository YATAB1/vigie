"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { authErrorMessage, validateEmail, validateNewPassword } from "@/lib/forms";
import { useSession } from "@/lib/session";
import type { User } from "@/lib/types";

const COPY = {
  login: {
    title: "Se connecter",
    submit: "Se connecter",
    path: "/api/v1/auth/login",
    other: { text: "Pas encore de compte ?", link: "Créer un compte", href: "/inscription" },
  },
  register: {
    title: "Créer un compte",
    submit: "Créer mon compte",
    path: "/api/v1/auth/register",
    other: { text: "Déjà un compte ?", link: "Se connecter", href: "/connexion" },
  },
} as const;

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const copy = COPY[mode];
  const router = useRouter();
  const { state, signIn } = useSession();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (state.status === "in") router.replace("/domaines");
  }, [state.status, router]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const problem =
      validateEmail(email) ?? (mode === "register" ? validateNewPassword(password) : null);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const user = await api.post<User>(copy.path, { email: email.trim(), password });
      signIn(user);
      router.replace("/domaines");
    } catch (caught) {
      setError(authErrorMessage(caught, mode));
      setBusy(false);
    }
  }

  return (
    <section className="auth">
      <h1>{copy.title}</h1>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <label className="field">
          <span>Courriel</span>
          <input
            type="email"
            name="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className="field">
          <span>Mot de passe</span>
          <input
            type="password"
            name="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-describedby={mode === "register" ? "password-hint" : undefined}
            required
          />
          {mode === "register" && (
            <small id="password-hint">12 caractères ou plus. Une phrase est plus sûre qu&apos;un mot.</small>
          )}
        </label>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? "Un instant…" : copy.submit}
        </button>
      </form>
      <p className="auth-switch">
        {copy.other.text} <Link href={copy.other.href}>{copy.other.link}</Link>
      </p>
    </section>
  );
}
