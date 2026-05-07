package io.github.carlos_emr.carlos.utility;
/**
 * Exception thrown to indicate an error related to Email Sending Exception.
 *
 * @since 2026-05-05
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
