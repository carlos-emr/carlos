package io.github.carlos_emr.carlos.utility;

/**
 * Custom runtime exception thrown when the system encounters a failure attempting to dispatch an email message.
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
