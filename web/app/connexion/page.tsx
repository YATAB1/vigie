import type { Metadata } from "next";
import { AuthForm } from "@/components/auth-form";

export const metadata: Metadata = { title: "Se connecter à Vigie" };

export default function LoginPage() {
  return <AuthForm mode="login" />;
}
