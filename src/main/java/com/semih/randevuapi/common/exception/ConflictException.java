package com.semih.randevuapi.common.exception;

/**
 * A request that clashes with the current state, answered with 409, e.g. closing a day that
 * still has bookings. The title becomes the problem-detail title, so clients can tell cases apart.
 */
public class ConflictException extends RuntimeException {

    private final String title;

    public ConflictException(String title, String message) {
        super(message);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
