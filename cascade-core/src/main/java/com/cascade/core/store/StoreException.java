package com.cascade.core.store;

/** Wraps the checked SQL layer so callers deal with one unchecked type. */
public class StoreException extends RuntimeException {

    public StoreException(String message) {
        super(message);
    }

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
