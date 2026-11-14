export type ApiStatus =
  | { state: "loading" }
  | { state: "up"; version: string }
  | { state: "down"; reason: string };

/** Shape returned by GET /api/v1/status. */
export interface StatusPayload {
  service: string;
  status: string;
  version: string;
}

export function isStatusPayload(value: unknown): value is StatusPayload {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.service === "string" &&
    typeof v.status === "string" &&
    typeof v.version === "string"
  );
}

/** Turns a raw HTTP outcome into the state the UI displays. */
export function toApiStatus(httpStatus: number, body: unknown): ApiStatus {
  if (httpStatus < 200 || httpStatus >= 300) {
    return { state: "down", reason: `HTTP ${httpStatus}` };
  }
  if (!isStatusPayload(body) || body.status !== "ok") {
    return { state: "down", reason: "réponse inattendue" };
  }
  return { state: "up", version: body.version };
}

export function statusLabel(status: ApiStatus): string {
  switch (status.state) {
    case "loading":
      return "Vérification de l'API…";
    case "up":
      return `API en ligne (v${status.version})`;
    case "down":
      return `API injoignable (${status.reason})`;
  }
}
