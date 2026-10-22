package com.semih.randevuapi.common.exception;

/** A request that is well-formed but breaks a business rule (answered with 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
