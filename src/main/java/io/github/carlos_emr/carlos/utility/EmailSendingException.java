package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown for EmailSending errors.
 *
 * This class is part of the CARLOS EMR system.
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
