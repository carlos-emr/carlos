package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception thrown when a failure occurs during the construction or transmission of an outbound email.
 */

public class EmailSendingException extends Exception {
    public EmailSendingException() {
    // Constructs a new EmailSendingException with the specified underlying cause.
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
