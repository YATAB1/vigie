package io.github.yatab1.vigie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yatab1.vigie.config.AuthProperties;
import io.github.yatab1.vigie.user.Role;
import io.github.yatab1.vigie.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private static JwtService serviceAt(Instant instant, String secret) {
        AuthProperties properties = new AuthProperties(
                secret, "vigie-api", Duration.ofMinutes(15), Duration.ofDays(7), false);
        return new JwtService(properties, new MockEnvironment(), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static User user(Role role) {
        User user = new User("yassine@example.com", "hash", role, NOW);
        ReflectionTestUtils.setField(user, "id", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return user;
    }

    @Test
    void verifiesATokenItIssued() {
        JwtService service = serviceAt(NOW, SECRET);

        AuthenticatedUser result = service.verify(service.issueAccessToken(user(Role.ADMIN))).orElseThrow();

        assertThat(result.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(result.email()).isEqualTo("yassine@example.com");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = serviceAt(NOW, SECRET).issueAccessToken(user(Role.USER));

        JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET);

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        byte[] otherKey = new byte[48];
        otherKey[0] = 1;
        String forged = serviceAt(NOW, Base64.getEncoder().encodeToString(otherKey))
                .issueAccessToken(user(Role.ADMIN));

        assertThat(serviceAt(NOW, SECRET).verify(forged)).isEmpty();
    }

    @Test
    void rejectsATamperedPayload() {
        JwtService service = serviceAt(NOW, SECRET);
        String[] parts = service.issueAccessToken(user(Role.USER)).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
                .replace("\"USER\"", "\"ADMIN\"");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes())
                + "." + parts[2];

        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsAnUnsignedToken() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = encoder.encodeToString(
                ("{\"iss\":\"vigie-api\",\"sub\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"email\":\"a@b.c\",\"role\":\"ADMIN\"}").getBytes());

        assertThat(serviceAt(NOW, SECRET).verify(header + "." + payload + ".")).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        JwtService service = serviceAt(NOW, SECRET);

        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatThrownBy(() -> serviceAt(NOW, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VIGIE_JWT_SECRET");
    }

    @Test
    void refusesAShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> serviceAt(NOW, shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
