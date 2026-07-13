package io.github.carlos_emr.carlos.utility;
/**
 * Custom runtime exception thrown when SMTP or API email transmission fails, providing context for communication subsystem errors.
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
