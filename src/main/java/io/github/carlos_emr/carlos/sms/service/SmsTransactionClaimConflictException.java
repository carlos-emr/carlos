package io.github.carlos_emr.carlos.sms.service;

final class SmsTransactionClaimConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SmsTransactionClaimConflictException(Long transactionId) {
        super("SMS transaction " + transactionId + " is no longer claimable for provider send.");
    }
}
