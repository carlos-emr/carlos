package io.github.carlos_emr.carlos.utility;
/**
 * Custom exception thrown when a specific EmailSending error occurs.
 * This allows upstream handlers to catch and format the EmailSendingException appropriately for the client.
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
