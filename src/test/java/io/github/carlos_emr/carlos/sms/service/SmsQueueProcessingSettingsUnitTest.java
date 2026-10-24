/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** How the queue worker finds the active provider and hands it the clinic's settings. */
@Tag("unit")
@Tag("service")
@DisplayName("SMS queue worker and provider settings")
class SmsQueueProcessingSettingsUnitTest {
    private static final SmsConsentDecisionDto CONSENTED = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"));
    private static final SmsProviderSettings SAVED = SmsProviderSettings.of(SmsProviderType.STUB, "+16135550100",
            Map.of("api_user", "fake-user"));

    private final InMemorySmsTransactionService transactions = new InMemorySmsTransactionService();
    private final RecordingProviderClient client = new RecordingProviderClient();

    @Test
    @DisplayName("sends nothing and fails nothing while the active provider cannot be determined")
    void shouldSkipRun_whenActiveProviderIsUnknown() {
        SmsTransaction queued = queued();

        int processed = worker(() -> {
            throw new IllegalStateException("Invalid sms.provider.default; configure a supported SMS provider.");
        }, type -> SAVED).processDueMessages(10);

        assertThat(processed).isZero();
        assertThat(queued.getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(client.sends).isEmpty();
    }

    @Test
    @DisplayName("leaves the active provider's rows waiting, unclaimed, while its settings cannot be read")
    void shouldLeaveRowsWaiting_whenSettingsCannotBeRead() {
        SmsTransaction queued = queued();
        SmsTransaction stale = stale();

        int processed = worker(() -> SmsProviderType.STUB, type -> {
            throw new SmsProviderNotReadyException("a stored SMS provider credential cannot be decrypted");
        }).processDueMessages(10);

        assertThat(processed).isZero();
        assertThat(queued).extracting(SmsTransaction::getStatus, SmsTransaction::getAttemptCount)
                .containsExactly(SmsStatus.QUEUED, 0);
        assertThat(stale.getStatus()).as("not taken over by stale recovery").isEqualTo(SmsStatus.SENDING);
        assertThat(client.sends).isEmpty();
        assertThat(client.lookups).isEmpty();
    }

    @Test
    @DisplayName("hands the active provider its settings for a send and for a stale-send lookup")
    void shouldHandSettingsToActiveProvider_forSendAndLookup() {
        queued();
        stale();

        worker(() -> SmsProviderType.STUB, type -> SAVED).processDueMessages(10);

        assertThat(client.sends).containsExactly(SAVED);
        assertThat(client.lookups).containsExactly(SAVED);
    }

    @Test
    @DisplayName("looks up a former provider's stale send with no settings, never the active provider's")
    void shouldLookUpFormerProvidersStaleSend_withNoSettings() {
        stale();

        worker(() -> SmsProviderType.VOIPMS, type -> SAVED).processDueMessages(10);

        assertThat(client.lookups).singleElement().satisfies(settings -> {
            assertThat(settings.providerType()).isEqualTo(SmsProviderType.STUB);
            assertThat(settings.credential("api_user")).isEmpty();
            assertThat(settings.senderNumber()).isEmpty();
        });
        assertThat(client.sends).isEmpty();
    }

    private SmsQueueProcessingService worker(Supplier<SmsProviderType> active,
                                             Function<SmsProviderType, SmsProviderSettings> settings) {
        return new SmsQueueProcessingService(transactions, new SmsProviderClientResolver(List.of(client)),
                new SmsRetryCalculator(), providerType -> true, command -> CONSENTED, active, settings);
    }

    private SmsTransaction queued() {
        return transactions.addQueued(SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE", "999998"),
                SmsProviderType.STUB, CONSENTED);
    }

    private SmsTransaction stale() {
        SmsTransaction transaction = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE", "999998"), SmsProviderType.STUB, CONSENTED);
        transaction.markSending(new Date(0));
        return transaction;
    }

    /** Records the settings it is handed; sends succeed, lookups cannot tell. */
    private static final class RecordingProviderClient extends StubSmsProviderClient {
        private final List<SmsProviderSettings> sends = new ArrayList<>();
        private final List<SmsProviderSettings> lookups = new ArrayList<>();

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId,
                                             SmsProviderSettings settings) {
            sends.add(settings);
            return super.send(command, clientReferenceId, settings);
        }

        @Override
        public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String providerMessageId,
                                                               SmsProviderSettings settings) {
            lookups.add(settings);
            return SmsProviderMessageStatusDto.unavailable("FAKE_LOOKUP", "The fake cannot tell.");
        }
    }
}
