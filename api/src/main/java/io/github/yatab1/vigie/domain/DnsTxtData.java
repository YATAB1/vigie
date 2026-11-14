package io.github.yatab1.vigie.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Payload of a "dns_txt" worker result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DnsTxtData(List<String> records) {
}
