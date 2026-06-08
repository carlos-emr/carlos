package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.springframework.stereotype.Service;

@Service
public class SmsSendService {
    private final SmsSendValidator validator;
    private final SmsConsentService consentService;
    private final SmsProviderResolver providerResolver;

    public SmsSendService(SmsSendValidator validator, SmsConsentService consentService, SmsProviderResolver providerResolver) {
        this.validator = validator;
        this.consentService = consentService;
        this.providerResolver = providerResolver;
    }

    public SmsSendResultDto send(SmsSendCommand command) {
        SmsSendValidator.Result validation = validator.validate(command);
        if (!validation.valid()) {
            return SmsSendResultDto.validationFailed(validation.messages());
        }

        SmsConsentDecisionDto consentDecision = consentService.evaluate(command);
        if (!consentDecision.allowed()) {
            return SmsSendResultDto.consentBlocked(consentDecision);
        }

        SmsProviderClient providerClient = providerResolver.resolve(SmsProviderType.STUB);
        SmsProviderSendResultDto providerResult = providerClient.send(command);
        return SmsSendResultDto.fromProvider(providerResult);
    }
}
