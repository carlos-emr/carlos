package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception representing a failure during the email transmission process.
 *
 * <p>Captures underlying SMTP or connection errors to provide actionable feedback
 * for the clinic's administrative staff.</p>
 */

public class EmailSendingException extends Exception {
    // Do not log full email content in the exception message to protect PHI
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
