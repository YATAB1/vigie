import { ApiStatusBadge } from "@/components/api-status-badge";

export default function HomePage() {
  return (
    <main className="home">
      <h1>Vigie</h1>
      <p className="tagline">
        Surveillance continue de la sécurité de vos domaines : en-têtes HTTP,
        TLS, DNS.
      </p>
      <ApiStatusBadge />
    </main>
  );
}
