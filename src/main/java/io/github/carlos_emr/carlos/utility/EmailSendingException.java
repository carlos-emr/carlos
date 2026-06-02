package io.github.carlos_emr.carlos.utility;

/**
 * Custom exception thrown when an error occurs during the email sending process.
 * Wraps underlying messaging or IO exceptions to provide a specific context for email delivery failures.
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
