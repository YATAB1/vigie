package io.github.yatab1.vigie.common;

import org.springframework.http.HttpStatus;

/** An error that maps directly to an HTTP status and a message safe to show to clients. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
