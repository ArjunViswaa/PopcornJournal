package com.popcornjournal.exception;

import java.util.List;

/** The request clashes with data already stored, e.g. a duplicate movie or a genre still in use. */
public class ConflictException extends RuntimeException {

    private final List<?> conflictsWith;

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<?> conflictsWith) {
        super(message);
        this.conflictsWith = conflictsWith;
    }

    public List<?> getConflictsWith() {
        return conflictsWith;
    }
}
