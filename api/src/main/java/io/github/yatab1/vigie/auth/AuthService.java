package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.common.ApiException;
import io.github.yatab1.vigie.config.AuthProperties;
import io.github.yatab1.vigie.user.Role;
import io.github.yatab1.vigie.user.User;
import io.github.yatab1.vigie.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Hash checked when the email is unknown, so both login paths take the same time. */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthSession register(String rawEmail, String password) {
        if (!fitsBcrypt(password)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is too long");
        }
        String email = normalizeEmail(rawEmail);
        if (users.existsByEmail(email)) {
            throw emailAlreadyUsed();
        }
        User user;
        try {
            user = users.saveAndFlush(
                    new User(email, passwordEncoder.encode(password), Role.USER, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            // Two registrations with the same email raced: the unique constraint decided.
            throw emailAlreadyUsed();
        }
        return openSession(user);
    }

    @Transactional
    public AuthSession login(String rawEmail, String password) {
        Optional<User> found = users.findByEmail(normalizeEmail(rawEmail));
        // Always run bcrypt, even for an unknown email. Otherwise the response time
        // would reveal which emails have an account.
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = fitsBcrypt(password) && passwordEncoder.matches(password, hash);
        if (found.isEmpty() || !passwordMatches) {
            // Same message for "unknown email" and "wrong password".
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return openSession(found.get());
    }

    /**
     * Exchanges a refresh token for a new token pair. Each refresh token is single-use:
     * it is revoked here and replaced. If a token that was already used shows up again,
     * it has been stolen or replayed, so every session of that user is revoked.
     *
     * <p>noRollbackFor: the revocation must be committed even though the request fails.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked()) {
            int revoked = refreshTokens.revokeAllForUser(token.getUserId(), now);
            log.warn("Refresh token reuse detected for user {}: {} session(s) revoked",
                    token.getUserId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        token.revoke(now);
        refreshTokens.save(token);
        return openSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token -> {
            token.revoke(clock.instant());
            refreshTokens.save(token);
        });
    }

    private AuthSession openSession(User user) {
        Instant now = clock.instant();
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(
                user.getId(), sha256(rawRefreshToken), now.plus(properties.refreshTokenTtl()), now));
        return new AuthSession(user, jwtService.issueAccessToken(user), rawRefreshToken);
    }

    private static ApiException emailAlreadyUsed() {
        return new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
