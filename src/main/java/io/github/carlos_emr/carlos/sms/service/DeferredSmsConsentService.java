package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.SmsTransactionType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.BooleanSupplier;

@Service
public class DeferredSmsConsentService implements SmsConsentService {
    static final String SYSTEM_TEST_ENABLED_PROPERTY = "sms.systemTest.enabled";

    private static final String CONSENT_MODEL_PENDING_CODE = "CONSENT_MODEL_PENDING";
    private static final String CONSENT_MODEL_PENDING_MESSAGE =
            "SMS consent integration is pending consent audit model issue #2674.";

    private final BooleanSupplier systemTestEnabled;

    @Autowired
    public DeferredSmsConsentService() {
        this(() -> CarlosProperties.getInstance().isPropertyActive(SYSTEM_TEST_ENABLED_PROPERTY));
    }

    DeferredSmsConsentService(BooleanSupplier systemTestEnabled) {
        if (systemTestEnabled == null) {
            this.systemTestEnabled = () -> false;
            return;
        }
        this.systemTestEnabled = systemTestEnabled;
    }

    @Override
    public SmsConsentDecisionDto evaluate(SmsSendCommand command) {
        if (isPermittedSystemTest(command)) {
            return SmsConsentDecisionDto.permit();
        }

        return SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED,
                CONSENT_MODEL_PENDING_CODE,
                CONSENT_MODEL_PENDING_MESSAGE
        );
    }

    private boolean isPermittedSystemTest(SmsSendCommand command) {
        return command != null
                && command.transactionType() == SmsTransactionType.SYSTEM_TEST
                && systemTestEnabled.getAsBoolean();
    }
}
