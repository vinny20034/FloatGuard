package com.floatguard.persistence;

/** Wraps checked {@link java.sql.SQLException}s so DAO code stays free of checked exceptions. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
