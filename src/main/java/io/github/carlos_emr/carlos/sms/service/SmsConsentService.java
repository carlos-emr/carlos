package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;

/**
 * SMS consent checks must consume the consent audit model from issue #2674.
 * Implementations should evaluate the command's selected recipient phone type
 * so consent can distinguish cell, home, work, and other phone numbers.
 * This boundary prevents SMS from creating a parallel consent store.
 */
public interface SmsConsentService {
    SmsConsentDecisionDto evaluate(SmsSendCommand command);
}
