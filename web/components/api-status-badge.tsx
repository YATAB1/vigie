"use client";

import { useEffect, useState } from "react";
import { type ApiStatus, statusLabel, toApiStatus } from "@/lib/api-status";

export function ApiStatusBadge() {
  const [status, setStatus] = useState<ApiStatus>({ state: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    async function check() {
      try {
        const response = await fetch("/api/v1/status", {
          signal: controller.signal,
          cache: "no-store",
        });
        const body: unknown = await response.json().catch(() => null);
        setStatus(toApiStatus(response.status, body));
      } catch (error) {
        if (controller.signal.aborted) return;
        const reason = error instanceof Error ? error.message : "erreur réseau";
        setStatus({ state: "down", reason });
      }
    }

    void check();
    return () => controller.abort();
  }, []);

  return (
    <p className="status" data-state={status.state} role="status">
      <span className="status-dot" aria-hidden="true" />
      {statusLabel(status)}
    </p>
  );
}
