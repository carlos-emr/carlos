package io.github.carlos_emr.carlos.utility;

public class EmailSendingException extends Exception {
    private final boolean deliveryOutcomeUncertain;

    public EmailSendingException() {
        super();
        this.deliveryOutcomeUncertain = false;
    }

    public EmailSendingException(String message) {
        super(message);
        this.deliveryOutcomeUncertain = false;
    }

    public EmailSendingException(Throwable cause) {
        super(cause);
        this.deliveryOutcomeUncertain = false;
    }

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
        this.deliveryOutcomeUncertain = false;
    }

    public EmailSendingException(String message, Throwable cause,
            boolean deliveryOutcomeUncertain) {
        super(message, cause);
        this.deliveryOutcomeUncertain = deliveryOutcomeUncertain;
    }

    /**
     * Returns whether a request may have reached the transport before the failure was observed.
     */
    public boolean isDeliveryOutcomeUncertain() {
        return deliveryOutcomeUncertain;
    }
}
