package io.github.yatab1.vigie.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostnamesTest {

    @Test
    void normalizesCaseSpacesAndTrailingDot() {
        assertThat(Hostnames.normalize("  Example.COM. ")).hasValue("example.com");
        assertThat(Hostnames.normalize("sub.example.co.uk")).hasValue("sub.example.co.uk");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "localhost",
        "127.0.0.1",
        "169.254.169.254",
        "[::1]",
        "example.com:8080",
        "example.com/admin",
        "http://example.com",
        "user@example.com",
        "-bad.example.com",
        "exa mple.com",
        "example..com",
        "_dmarc.example.com",
        "printer.local",
        "db.internal",
        "service.localhost"
    })
    void rejectsAnythingThatIsNotAPublicHostname(String value) {
        assertThat(Hostnames.normalize(value)).isEmpty();
    }

    @Test
    void rejectsNullAndOverlongNames() {
        assertThat(Hostnames.normalize(null)).isEmpty();
        assertThat(Hostnames.normalize("a".repeat(64) + ".com")).isEmpty();
        assertThat(Hostnames.normalize("a.".repeat(130) + "com")).isEmpty();
    }
}
