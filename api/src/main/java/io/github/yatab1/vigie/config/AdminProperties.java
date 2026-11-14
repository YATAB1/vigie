package io.github.yatab1.vigie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vigie.admin")
public record AdminProperties(String email, String password) {
}
