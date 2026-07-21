package io.github.carlos_emr.carlos.utility;

/**
 * Custom exception thrown when an error occurs during the email sending process.
 * Encapsulates underlying messaging exceptions and provides specific context
 * about failures in the SMTP or API transport layers.
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
