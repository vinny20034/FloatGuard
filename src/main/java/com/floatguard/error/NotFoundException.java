package com.floatguard.error;

/** A referenced agent / wallet / rebalance does not exist -> HTTP 404 Not Found. */
public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(message);
    }
}
