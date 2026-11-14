package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSession session = authService.register(request.email(), request.password());
        return withSession(HttpStatus.CREATED, session);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthSession session = authService.login(request.email(), request.password());
        return withSession(HttpStatus.OK, session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        AuthSession session = authService.refresh(refreshToken);
        return withSession(HttpStatus.OK, session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.clearAccess().toString(),
                        cookies.clearRefresh().toString())
                .build();
    }

    /** Tokens travel only in cookies: the response body never contains them. */
    private ResponseEntity<UserResponse> withSession(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.access(session.accessToken()).toString(),
                        cookies.refresh(session.refreshToken()).toString())
                .body(UserResponse.from(session.user()));
    }
}
