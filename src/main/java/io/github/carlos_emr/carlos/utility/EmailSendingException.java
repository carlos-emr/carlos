package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown when an error occurs during the email sending process.
 */
public class EmailSendingException extends Exception {
    public EmailSendingException() {
        // Initialize execution context for EmailSendingException

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
