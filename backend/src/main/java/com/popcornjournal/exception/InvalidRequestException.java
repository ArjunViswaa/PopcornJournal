package com.popcornjournal.exception;

/** A request that passed bean validation but still can't be processed, e.g. an unreadable spreadsheet. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
