import { describe, expect, it } from "vitest";
import { statusLabel, toApiStatus } from "./api-status";

describe("toApiStatus", () => {
  it("reports the API as up when the payload is valid", () => {
    const status = toApiStatus(200, {
      service: "vigie-api",
      status: "ok",
      version: "0.1.0",
    });
    expect(status).toEqual({ state: "up", version: "0.1.0" });
  });

  it("reports the API as down on a non-2xx response", () => {
    expect(toApiStatus(502, null)).toEqual({ state: "down", reason: "HTTP 502" });
  });

  it("rejects a 200 response with an unexpected body", () => {
    expect(toApiStatus(200, { hello: "world" }).state).toBe("down");
    expect(toApiStatus(200, "ok").state).toBe("down");
  });

  it("rejects a payload whose status is not ok", () => {
    const status = toApiStatus(200, {
      service: "vigie-api",
      status: "degraded",
      version: "0.1.0",
    });
    expect(status.state).toBe("down");
  });
});

describe("statusLabel", () => {
  it("includes the version when the API is up", () => {
    expect(statusLabel({ state: "up", version: "0.1.0" })).toContain("0.1.0");
  });

  it("includes the reason when the API is down", () => {
    expect(statusLabel({ state: "down", reason: "HTTP 500" })).toContain("HTTP 500");
  });
});
