package io.github.carlos_emr.carlos.utility;

/**
 * Thrown to indicate a failure during the transmission of an email via the configured SMTP provider or local mailer, wrapping the underlying transport error.
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
