package io.github.yatab1.vigie.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validation of the hostnames users submit. */
public final class Hostnames {

    private static final int MAX_LENGTH = 253;
    private static final String LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    // At least two labels and an alphabetic TLD: rejects IP addresses, ports,
    // paths, URLs and single-label names such as "localhost".
    private static final Pattern HOSTNAME =
            Pattern.compile("^(?:" + LABEL + "\\.)+(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$");
    private static final List<String> INTERNAL_SUFFIXES =
            List.of(".local", ".localhost", ".internal", ".lan", ".home", ".corp");

    private Hostnames() {
    }

    /** Returns the canonical form of a public hostname, or empty if it is not one. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String hostname = raw.trim().toLowerCase(Locale.ROOT);
        if (hostname.endsWith(".")) {
            hostname = hostname.substring(0, hostname.length() - 1);
        }
        if (hostname.length() > MAX_LENGTH || !HOSTNAME.matcher(hostname).matches()) {
            return Optional.empty();
        }
        for (String suffix : INTERNAL_SUFFIXES) {
            if (hostname.endsWith(suffix)) {
                return Optional.empty();
            }
        }
        return Optional.of(hostname);
    }
}
