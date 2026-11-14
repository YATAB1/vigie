package io.github.yatab1.vigie.scan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/** Payload of a successful "scan" worker result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanData(Integer score, String grade, JsonNode checks) {
}
