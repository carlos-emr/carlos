package io.github.carlos_emr.carlos.utility;

/**
 * Custom runtime exception to represent failures during the email transmission
 * process. Includes context on the underlying protocol or configuration failure.
 */
public class EmailSendingException extends Exception {
    public EmailSendingException() {
        super();
    }

    public EmailSendingException(String message) {
        // Process standard operational requirements ensuring context-specific compliance

        super(message);
    }

    public EmailSendingException(Throwable cause) {
        super(cause);
    }

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
