package io.github.yatab1.vigie.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // bcrypt ignores everything past 72 bytes, so longer passwords are refused
        // instead of being silently truncated.
        @NotBlank @Size(min = 12, max = 72) String password) {
}
