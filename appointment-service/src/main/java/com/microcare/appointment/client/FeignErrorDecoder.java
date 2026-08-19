package com.microcare.appointment.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus httpStatus = HttpStatus.valueOf(response.status());

        log.error("Feign client error for method [{}] - Status: {}, Reason: {}",
                methodKey, response.status(), response.reason());

        return switch (httpStatus) {
            case NOT_FOUND -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Requested resource not found in downstream service"
            );
            case SERVICE_UNAVAILABLE -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Downstream service is temporarily unavailable"
            );
            case INTERNAL_SERVER_ERROR -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Downstream service encountered an internal error"
            );
            default -> defaultErrorDecoder.decode(methodKey, response);
        };
    }
}
