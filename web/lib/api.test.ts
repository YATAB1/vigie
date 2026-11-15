import { describe, expect, it } from "vitest";
import { ApiError, createApi } from "./api";

type Call = { path: string; method: string; body?: string };

function json(status: number, body: unknown = {}): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Fake fetch answering from a script keyed by "METHOD path". */
function fakeFetch(script: Record<string, Array<() => Response | Promise<Response>>>) {
  const calls: Call[] = [];
  const fetchImpl = async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push({ path, method, body: init?.body as string | undefined });
    const next = script[`${method} ${path}`]?.shift();
    if (!next) throw new Error(`unexpected request: ${method} ${path}`);
    return next();
  };
  return { fetchImpl, calls };
}

describe("api client", () => {
  it("returns the parsed body of a successful request", async () => {
    const { fetchImpl } = fakeFetch({ "GET /api/v1/me": [() => json(200, { email: "a@b.c" })] });

    await expect(createApi(fetchImpl).get("/api/v1/me")).resolves.toEqual({ email: "a@b.c" });
  });

  it("sends a JSON body with the right content type", async () => {
    const { fetchImpl, calls } = fakeFetch({ "POST /api/v1/domains": [() => json(201, {})] });

    await createApi(fetchImpl).post("/api/v1/domains", { hostname: "example.com" });

    expect(calls[0].body).toBe('{"hostname":"example.com"}');
  });

  it("refreshes the session once and retries after a 401", async () => {
    const { fetchImpl, calls } = fakeFetch({
      "GET /api/v1/domains": [() => json(401), () => json(200, [{ id: "1" }])],
      "POST /api/v1/auth/refresh": [() => json(200)],
    });

    await expect(createApi(fetchImpl).get("/api/v1/domains")).resolves.toEqual([{ id: "1" }]);
    expect(calls.map((c) => `${c.method} ${c.path}`)).toEqual([
      "GET /api/v1/domains",
      "POST /api/v1/auth/refresh",
      "GET /api/v1/domains",
    ]);
  });

  it("reports 401 when the session cannot be refreshed", async () => {
    const { fetchImpl } = fakeFetch({
      "GET /api/v1/me": [() => json(401)],
      "POST /api/v1/auth/refresh": [() => json(401)],
    });

    await expect(createApi(fetchImpl).get("/api/v1/me")).rejects.toMatchObject({ status: 401 });
  });

  it("shares a single refresh between simultaneous requests", async () => {
    let releaseRefresh: (response: Response) => void = () => {};
    const { fetchImpl, calls } = fakeFetch({
      "GET /api/v1/a": [() => json(401), () => json(200, "a")],
      "GET /api/v1/b": [() => json(401), () => json(200, "b")],
      // Only one scripted refresh: a second call would throw "unexpected request".
      "POST /api/v1/auth/refresh": [() => new Promise<Response>((r) => (releaseRefresh = r))],
    });
    const client = createApi(fetchImpl);

    const both = Promise.all([client.get("/api/v1/a"), client.get("/api/v1/b")]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    releaseRefresh(json(200));

    await expect(both).resolves.toEqual(["a", "b"]);
    expect(calls.filter((c) => c.path.endsWith("/refresh"))).toHaveLength(1);
  });

  it("does not try to refresh when a login itself is rejected", async () => {
    const { fetchImpl, calls } = fakeFetch({
      "POST /api/v1/auth/login": [() => json(401, { detail: "Invalid email or password" })],
    });

    await expect(createApi(fetchImpl).post("/api/v1/auth/login", {})).rejects.toMatchObject({
      status: 401,
      message: "Invalid email or password",
    });
    expect(calls).toHaveLength(1);
  });

  it("exposes field errors from a validation failure", async () => {
    const { fetchImpl } = fakeFetch({
      "POST /api/v1/auth/register": [
        () => json(400, { detail: "Validation failed", errors: { password: "too short" } }),
      ],
    });

    const error = await createApi(fetchImpl)
      .post("/api/v1/auth/register", {})
      .catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).fieldErrors).toEqual({ password: "too short" });
  });

  it("survives an error response that is not JSON", async () => {
    const { fetchImpl } = fakeFetch({
      "GET /api/v1/me": [() => new Response("<html>Bad Gateway</html>", { status: 502 })],
    });

    await expect(createApi(fetchImpl).get("/api/v1/me")).rejects.toMatchObject({
      status: 502,
      message: "HTTP 502",
    });
  });

  it("returns nothing for 204 responses", async () => {
    const { fetchImpl } = fakeFetch({ "DELETE /api/v1/domains/1": [() => json(204)] });

    await expect(createApi(fetchImpl).delete("/api/v1/domains/1")).resolves.toBeUndefined();
  });
});
