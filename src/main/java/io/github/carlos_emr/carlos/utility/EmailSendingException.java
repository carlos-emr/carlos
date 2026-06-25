package io.github.carlos_emr.carlos.utility;

/**
 * Custom runtime exception indicating a emailsending error state.
 * Thrown when the system encounters an unrecoverable state during emailsending processing.
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
