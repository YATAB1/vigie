package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.user.Role;
import java.util.UUID;

/** Identity of the caller, extracted from a verified access token. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
