package com.automation.exceptions;

/**
 * Thrown when configuration is missing, malformed, or cannot be loaded.
 * Separate from FrameworkException so callers can distinguish config
 * problems (usually setup issues) from runtime automation failures.
 */
public class ConfigException extends FrameworkException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
