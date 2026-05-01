package com.automation.exceptions;

/**
 * Root unchecked exception for all framework-level failures.
 * Wrapping checked exceptions here prevents test code from needing
 * try-catch blocks around infrastructure calls.
 */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }

    public FrameworkException(Throwable cause) {
        super(cause);
    }
}
