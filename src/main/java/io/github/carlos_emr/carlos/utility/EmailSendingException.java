package io.github.carlos_emr.carlos.utility;
/**
 * EmailSendingException provides functionality and data models for the EmailSendingException domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
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
