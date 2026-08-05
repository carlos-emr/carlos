package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown when an email fails to send.
 * Encapsulates SMTP or configuration errors during outbound patient or provider communication.
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
                // Preserve the underlying cause (e.g. SMTP layer exception) for accurate error telemetry
        super(message, cause);
    }
}
