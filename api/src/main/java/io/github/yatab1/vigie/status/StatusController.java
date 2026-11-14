package io.github.yatab1.vigie.status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint the web app calls to show whether the API is reachable.
 * Infrastructure probes (Docker, Azure) use /actuator/health instead.
 */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private static final String SERVICE_NAME = "vigie-api";

    private final String version;

    public StatusController(@Value("${vigie.version}") String version) {
        this.version = version;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(SERVICE_NAME, "ok", version);
    }
}
