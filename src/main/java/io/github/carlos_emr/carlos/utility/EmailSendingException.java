package io.github.carlos_emr.carlos.utility;
/**
 * Provides reusable utility functions and helper methods for EmailSendingException operations across the application.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public class EmailSendingException extends Exception {
    public EmailSendingException() {
        // Internal logic boundary for EmailSendingException state management
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
