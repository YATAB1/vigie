import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import {
  authErrorMessage,
  cleanHostname,
  domainErrorMessage,
  validateEmail,
  validateNewPassword,
} from "./forms";

describe("cleanHostname", () => {
  it.each([
    ["example.com", "example.com"],
    ["  Example.COM  ", "example.com"],
    ["https://example.com", "example.com"],
    ["https://www.example.com/contact?x=1#top", "www.example.com"],
    ["http://example.com:8080/admin", "example.com"],
    ["example.com.", "example.com"],
    ["user:pass@example.com", "example.com"],
  ])("turns %s into %s", (input, expected) => {
    expect(cleanHostname(input)).toBe(expected);
  });
});

describe("validation", () => {
  it("accepts a plausible email and rejects the rest", () => {
    expect(validateEmail("yassine@example.com")).toBeNull();
    expect(validateEmail("yassine@example")).not.toBeNull();
    expect(validateEmail("not an email")).not.toBeNull();
  });

  it("requires 12 characters and at most 72 bytes", () => {
    expect(validateNewPassword("short")).toContain("12");
    expect(validateNewPassword("correct horse battery")).toBeNull();
    expect(validateNewPassword("é".repeat(40))).toBe("Le mot de passe est trop long.");
  });
});

describe("error messages", () => {
  it("never reveals whether the email or the password was wrong", () => {
    expect(authErrorMessage(new ApiError(401, "x"), "login")).toBe(
      "Courriel ou mot de passe incorrect.",
    );
  });

  it("explains a blocked account", () => {
    expect(authErrorMessage(new ApiError(429, "x"), "login")).toContain("Trop de tentatives");
  });

  it("explains a duplicate account and a duplicate domain", () => {
    expect(authErrorMessage(new ApiError(409, "x"), "register")).toContain("existe déjà");
    expect(domainErrorMessage(new ApiError(409, "x"))).toContain("déjà dans votre liste");
  });

  it("falls back to a generic message on network failures", () => {
    expect(authErrorMessage(new TypeError("fetch failed"), "login")).toContain("ne répond pas");
  });
});
