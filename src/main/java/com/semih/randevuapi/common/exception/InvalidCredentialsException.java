package com.semih.randevuapi.common.exception;

/** Deliberately vague: does not reveal whether the email or the password was wrong. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
