package io.github.carlos_emr.carlos.utility;

/**
 * Utility class providing helper methods for EmailSendingException operations.
 * Isolates complex format transformations and standardizes reusable operations across different application layers.
 */
public class EmailSendingException extends Exception {
    public EmailSendingException() {
        super();
    }

    public EmailSendingException(String message) {
        super(message);
    }

    public EmailSendingException(Throwable cause) {
        super(cause);
    }

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
