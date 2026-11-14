package io.github.yatab1.vigie.auth;

import io.github.yatab1.vigie.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Session expired, please sign in again");
    }
}
