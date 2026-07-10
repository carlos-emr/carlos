package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception thrown when an error occurs during email sending operations.
 * Used to wrap and propagate underlying SMTP or configuration failures.
 *
 * @since 2026-07-09
 */

public class EmailSendingException extends Exception {
    public EmailSendingException() {
        // Call superclass constructor to propagate the root cause exception
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
