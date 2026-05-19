package io.github.carlos_emr.carlos.utility;

/**
 * Indicates that a user-supplied filename or file path failed validation.
 *
 * <p>This exception is deliberately unchecked because most validation failures
 * are request-security failures. Upload and document actions should still catch
 * it explicitly where they need to return a user-facing validation response.</p>
 */
public class FileValidationException extends SecurityException {

    public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
