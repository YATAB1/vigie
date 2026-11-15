import { ApiError } from "./api";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 12;
const MAX_PASSWORD_BYTES = 72;

export function validateEmail(email: string): string | null {
  return EMAIL.test(email.trim()) ? null : "Entrez une adresse courriel valide.";
}

export function validateNewPassword(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Le mot de passe doit contenir au moins ${MIN_PASSWORD_LENGTH} caractères.`;
  }
  if (new TextEncoder().encode(password).length > MAX_PASSWORD_BYTES) {
    return "Le mot de passe est trop long.";
  }
  return null;
}

/**
 * People paste addresses from their browser: "https://Example.com/contact".
 * Keep only the host so the API receives "example.com".
 */
export function cleanHostname(input: string): string {
  let value = input.trim().toLowerCase();
  value = value.replace(/^[a-z][a-z0-9+.-]*:\/\//, "");
  value = value.split(/[/?#]/)[0];
  value = value.replace(/^[^@]*@/, "").replace(/:\d+$/, "").replace(/\.$/, "");
  return value;
}

export function authErrorMessage(error: unknown, mode: "login" | "register"): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Courriel ou mot de passe incorrect.";
    if (error.status === 429) {
      return "Trop de tentatives de connexion. Réessayez dans 15 minutes.";
    }
    if (error.status === 409) return "Un compte existe déjà avec ce courriel.";
    if (error.status === 400) {
      return mode === "register"
        ? "Vérifiez le courriel et le mot de passe (12 caractères minimum)."
        : "Vérifiez le courriel et le mot de passe.";
    }
  }
  return "Le service ne répond pas. Réessayez dans un instant.";
}

export function domainErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "Entrez un nom de domaine public, par exemple exemple.com.";
    }
    if (error.status === 409) return "Ce domaine est déjà dans votre liste.";
  }
  return "Le domaine n'a pas pu être ajouté. Réessayez dans un instant.";
}

export function scanErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) return "Un scan est déjà en cours pour ce domaine.";
    if (error.status === 503) return "Le service de scan est indisponible pour le moment.";
  }
  return "Le scan n'a pas pu être lancé. Réessayez dans un instant.";
}
