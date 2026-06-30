package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception representing failures in external email transport or configuration.
 * Encapsulates SMTP or connection errors to allow graceful error handling by callers.
 */

public class EmailSendingException extends Exception {
    public EmailSendingException() {
        // Wrap connection faults to prevent raw SMTP details from leaking to the UI
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
