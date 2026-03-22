package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown when an email cannot be sent successfully.
 *
 * <p>Used by the email notification subsystem to indicate failures in
 * SMTP communication, message composition, or recipient resolution.
 *
 * @since 2026-03-17
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
