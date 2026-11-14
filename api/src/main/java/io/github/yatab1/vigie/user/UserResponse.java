package io.github.yatab1.vigie.user;

import java.time.Instant;
import java.util.UUID;

/** Public view of a user. The password hash never leaves the service layer. */
public record UserResponse(UUID id, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
