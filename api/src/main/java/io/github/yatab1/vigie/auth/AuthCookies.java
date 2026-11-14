package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.config.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the two authentication cookies. Both are HttpOnly (JavaScript cannot read
 * them, so an XSS flaw cannot steal a session) and SameSite=Strict (the browser does
 * not send them on cross-site requests, which blocks CSRF).
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "vigie_access";
    public static final String REFRESH = "vigie_refresh";

    /** The refresh token is only ever sent to the auth endpoints. */
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie access(String token) {
        return build(ACCESS, token, "/", properties.accessTokenTtl());
    }

    public ResponseCookie refresh(String token) {
        return build(REFRESH, token, REFRESH_PATH, properties.refreshTokenTtl());
    }

    public ResponseCookie clearAccess() {
        return build(ACCESS, "", "/", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return build(REFRESH, "", REFRESH_PATH, Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
