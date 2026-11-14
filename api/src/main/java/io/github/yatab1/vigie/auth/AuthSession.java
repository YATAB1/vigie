package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.user.User;

/** Result of a successful register, login or refresh: the user and a fresh token pair. */
public record AuthSession(User user, String accessToken, String refreshToken) {
}
