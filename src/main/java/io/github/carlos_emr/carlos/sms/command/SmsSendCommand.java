package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsTransactionType;

public record SmsSendCommand(
        Integer demographicNo,
        String recipientPhoneNumber,
        String body,
        SmsTransactionType transactionType,
        String providerNo,
        Integer appointmentNo,
        Integer relatedId
) {
    public SmsSendCommand {
        if (transactionType == null) {
            transactionType = SmsTransactionType.DIRECT;
        }
    }

    public static SmsSendCommand direct(Integer demographicNo, String recipientPhoneNumber, String body, String providerNo) {
        return new SmsSendCommand(demographicNo, recipientPhoneNumber, body, SmsTransactionType.DIRECT, providerNo, null, null);
    }
}
