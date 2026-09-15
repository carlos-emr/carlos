package io.github.carlos_emr.carlos.sms;

public enum SmsStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    RECEIVED,
    FAILED,
    CONSENT_BLOCKED,
    OPTOUT_BLOCKED
}
