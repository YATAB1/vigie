package io.github.yatab1.vigie.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDomainRequest(@NotBlank @Size(max = 255) String hostname) {
}
