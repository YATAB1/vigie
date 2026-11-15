import type { Metadata } from "next";
import "@fontsource-variable/bricolage-grotesque";
import "@fontsource-variable/public-sans";
import "./globals.css";
import { AppShell } from "@/components/app-shell";
import { SessionProvider } from "@/lib/session";

export const metadata: Metadata = {
  title: "Vigie, surveillance de sécurité web",
  description:
    "Vigie analyse la configuration de sécurité de vos domaines : chiffrement, en-têtes HTTP, protection du courriel.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body>
        <SessionProvider>
          <AppShell>{children}</AppShell>
        </SessionProvider>
      </body>
    </html>
  );
}
