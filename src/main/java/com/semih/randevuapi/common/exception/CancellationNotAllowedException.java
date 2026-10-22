package com.semih.randevuapi.common.exception;

/** A booking that can no longer be cancelled: already cancelled, already started, or too close (409). */
public class CancellationNotAllowedException extends RuntimeException {

    public CancellationNotAllowedException(String message) {
        super(message);
    }
}
