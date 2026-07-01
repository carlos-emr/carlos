package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception representing failures during email transmission.
 * <p>
 * Used by the CARLOS EMR messaging system to wrap underlying SMTP or network errors
 * for consistent error handling and logging.
 * </p>
 */


public class EmailSendingException extends Exception {
    public EmailSendingException() {
        // Wrap underlying mail exception to ensure standard error handling across email components.
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
