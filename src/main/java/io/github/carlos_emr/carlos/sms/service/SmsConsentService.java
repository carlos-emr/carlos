package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;

/**
 * Decides whether an outbound SMS may be sent, and reports the consent record the decision relied on.
 * <p>
 * Implementations must read the shared CARLOS consent records; this boundary exists so SMS never grows a
 * parallel consent store. Callers evaluate at queue admission and again before a queued dispatch, so an
 * implementation must be safe to call without a user session. It may throw when consent cannot be
 * determined; callers must then not send.
 * <p>
 * The command's recipient phone type is available so consent can later distinguish cell, home, work and
 * other numbers once the consent audit model (issue #2674) supports phone-scoped consent.
 */
public interface SmsConsentService {
    SmsConsentDecisionDto evaluate(SmsSendCommand command);
}
