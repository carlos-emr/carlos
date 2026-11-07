package io.github.carlos_emr.carlos.utility;

public class EmailSendingException extends Exception {

    /**
     * Which address the mail server refused, when a transport can prove the message never left.
     * It exists so staff can be told what to check; it is never used to decide the outcome.
     */
    public enum Refusal {
        /** No refusal is known: another kind of failure, or an outcome that is not certain. */
        NONE,
        /** The server refused the recipient addresses (SMTP {@code RCPT TO}). */
        RECIPIENT,
        /** The server refused the sending address (SMTP {@code MAIL FROM}). */
        SENDER
    }

    private final boolean deliveryOutcomeUncertain;
    private final Refusal refusal;

    public EmailSendingException() {
        super();
        this.deliveryOutcomeUncertain = false;
        this.refusal = Refusal.NONE;
    }

    public EmailSendingException(String message) {
        super(message);
        this.deliveryOutcomeUncertain = false;
        this.refusal = Refusal.NONE;
    }

    public EmailSendingException(Throwable cause) {
        super(cause);
        this.deliveryOutcomeUncertain = false;
        this.refusal = Refusal.NONE;
    }

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
        this.deliveryOutcomeUncertain = false;
        this.refusal = Refusal.NONE;
    }

    public EmailSendingException(String message, Throwable cause,
            boolean deliveryOutcomeUncertain) {
        super(message, cause);
        this.deliveryOutcomeUncertain = deliveryOutcomeUncertain;
        this.refusal = Refusal.NONE;
    }

    /**
     * A definite failure: the server refused the message, so it was not sent. Only for failures
     * the transport can prove happened before the message was accepted.
     */
    public EmailSendingException(String message, Throwable cause, Refusal refusal) {
        super(message, cause);
        this.deliveryOutcomeUncertain = false;
        this.refusal = refusal == null ? Refusal.NONE : refusal;
    }

    /**
     * Returns whether a request may have reached the transport before the failure was observed.
     */
    public boolean isDeliveryOutcomeUncertain() {
        return deliveryOutcomeUncertain;
    }

    /**
     * Returns which address the server refused, or {@link Refusal#NONE}. Always NONE when the
     * outcome is uncertain.
     */
    public Refusal getRefusal() {
        return refusal;
    }
}
