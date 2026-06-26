package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown to indicate specific errors in email sending operations.
 */
public class EmailSendingException extends Exception {
    // Custom exception to capture failures during email transmission

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
