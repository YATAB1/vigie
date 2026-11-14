package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.config.AuthProperties;
import io.github.yatab1.vigie.user.Role;
import io.github.yatab1.vigie.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/** Issues and verifies the short-lived access tokens (HS256-signed JWTs). */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtService(AuthProperties properties, Environment environment, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = new SecretKeySpec(resolveSecret(properties.jwtSecret(), environment), "HmacSHA256");
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Returns the caller's identity if the token has a valid signature, the expected
     * issuer and has not expired. Any other token (tampered, unsigned, expired,
     * malformed) yields an empty result.
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static byte[] resolveSecret(String configured, Environment environment) {
        if (configured == null || configured.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("dev"))) {
                log.warn("VIGIE_JWT_SECRET is not set: using a random key (dev profile only). "
                        + "Sessions will not survive a restart.");
                byte[] random = new byte[48];
                new SecureRandom().nextBytes(random);
                return random;
            }
            throw new IllegalStateException(
                    "VIGIE_JWT_SECRET is required. Generate one with: openssl rand -base64 48");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("VIGIE_JWT_SECRET must be Base64-encoded", exception);
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "VIGIE_JWT_SECRET must decode to at least " + MIN_SECRET_BYTES + " bytes");
        }
        return secret;
    }
}
