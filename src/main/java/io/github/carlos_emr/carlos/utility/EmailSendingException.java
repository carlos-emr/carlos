package io.github.carlos_emr.carlos.utility;
/**
 * Exception thrown to indicate a failure during the transmission of an email message.
 * <p>
 * This can be caused by configuration errors, network issues, or SMTP server rejections.
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
