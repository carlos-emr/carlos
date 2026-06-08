package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import org.springframework.stereotype.Service;

@Service
public class DeferredSmsConsentService implements SmsConsentService {
    @Override
    public SmsConsentDecisionDto evaluate(SmsSendCommand command) {
        return SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                "CONSENT_MODEL_PENDING",
                "SMS consent integration is pending consent audit model issue #2674."
        );
    }
}
