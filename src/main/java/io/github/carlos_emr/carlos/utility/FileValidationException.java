package io.github.carlos_emr.carlos.utility;

/**
 * Indicates that a user-supplied filename or file path failed validation.
 */
public class FileValidationException extends RuntimeException {

    public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
