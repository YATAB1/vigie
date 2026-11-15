package io.github.yatab1.vigie.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from      sender address of alert emails
 * @param publicUrl address of the web app, used to link to the report
 */
@ConfigurationProperties(prefix = "vigie.alerts")
public record AlertProperties(String from, String publicUrl) {
}
