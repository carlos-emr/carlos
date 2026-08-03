package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception thrown when the system encounters a failure attempting to send an email.
 * This typically wraps underlying messaging exceptions or SMTP connection errors.
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
