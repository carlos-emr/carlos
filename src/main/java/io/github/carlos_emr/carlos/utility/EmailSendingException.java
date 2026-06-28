package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception indicating a specific error condition related to EmailSending.
 *
 * @since 2026-06-26
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
