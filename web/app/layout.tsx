import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Vigie — surveillance de sécurité web",
  description:
    "Vigie audite en continu la configuration de sécurité de vos domaines.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
