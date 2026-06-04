package io.github.carlos_emr.carlos.utility;

/**
 * Custom exception thrown when an email transmission fails due to network issues, invalid credentials, or SMTP errors.
 * Allows upper layers to gracefully handle communication failures without exposing underlying API complexities.
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
