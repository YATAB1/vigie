/**
 * Client for the Vigie API. Requests go to this same origin (Next.js relays
 * /api/* to the API), so the session cookies are attached automatically and
 * no token is ever handled by JavaScript.
 */

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

export interface Api {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  delete(path: string): Promise<void>;
}

const REFRESH_PATH = "/api/v1/auth/refresh";

export function createApi(fetchImpl: FetchLike = (input, init) => fetch(input, init)): Api {
  // A refresh token is single-use. If several requests fail with 401 at the same
  // time, they must share ONE refresh call: a second call would present an
  // already-used token, which the API treats as theft and answers by closing
  // every session of the account.
  let refreshInFlight: Promise<boolean> | null = null;

  function refreshSession(): Promise<boolean> {
    refreshInFlight ??= fetchImpl(REFRESH_PATH, { method: "POST", credentials: "same-origin" })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
    return refreshInFlight;
  }

  function send(method: string, path: string, body?: unknown): Promise<Response> {
    return fetchImpl(path, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    let response = await send(method, path, body);
    const isAuthRoute = path.startsWith("/api/v1/auth/");
    if (response.status === 401 && !isAuthRoute && (await refreshSession())) {
      response = await send(method, path, body);
    }
    if (!response.ok) {
      throw await toApiError(response);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return {
    get: (path) => request("GET", path),
    post: (path, body) => request("POST", path, body),
    delete: (path) => request<void>("DELETE", path),
  };
}

async function toApiError(response: Response): Promise<ApiError> {
  let detail = `HTTP ${response.status}`;
  let fieldErrors: Record<string, string> = {};
  try {
    const problem: unknown = await response.json();
    if (typeof problem === "object" && problem !== null) {
      const record = problem as Record<string, unknown>;
      if (typeof record.detail === "string") detail = record.detail;
      if (typeof record.errors === "object" && record.errors !== null) {
        fieldErrors = Object.fromEntries(
          Object.entries(record.errors as Record<string, unknown>).filter(
            (entry): entry is [string, string] => typeof entry[1] === "string",
          ),
        );
      }
    }
  } catch {
    // The body was not JSON (proxy error page, empty body): keep the status only.
  }
  return new ApiError(response.status, detail, fieldErrors);
}

export const api = createApi();
