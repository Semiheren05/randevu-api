package com.semih.randevuapi.common.exception;

/** The chosen time is no longer free (answered with 409). */
public class SlotNotAvailableException extends RuntimeException {

    public SlotNotAvailableException() {
        super("This time is not available, please choose another slot");
    }
}
