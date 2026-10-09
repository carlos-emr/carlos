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
import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.assembler.SmsConfigViewModelAssembler;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsDeliveryWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsConfigValidator;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsConfigViewModel;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves that a clinic can swap SMS providers with no change to shared code. A fake second provider, registered
 * under the unused name {@code CLOUDLI}, brings its own credentials, sender-number need, rate limit, number
 * format and callback check; the shared settings page, validation, direct send, queue worker, rate limiter and
 * callback service all work with it as they are.
 */
@Tag("unit")
@Tag("service")
@DisplayName("Swapping SMS providers")
class SmsProviderSwapUnitTest {
    private static final SmsConsentDecisionDto CONSENTED = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z"));
    private static final String SENDER = "+16135550100";

    private final AtomicReference<SmsConfig> savedRow = new AtomicReference<>();
    private final FakeSecondProviderClient fake = new FakeSecondProviderClient();
    private final CountingStubProviderClient stub = new CountingStubProviderClient();
    private final InMemorySmsTransactionService transactions = new InMemorySmsTransactionService();
    private SmsProviderClientResolver clients;
    private SmsConfigService configService;
    private SmsDefaultProviderResolver activeProvider;
    private String originalKey;

    @BeforeEach
    void setUp() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
        install(stub, fake);
        save(settings(SmsProviderType.STUB, true, "", Map.of()));
    }

    @AfterEach
    void restoreKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @DisplayName("the settings page shows and checks the new provider's own credentials and sender number")
    void shouldShowAndCheckNewProvidersNeeds_whenClinicChoosesIt() {
        SmsConfigViewModelAssembler page = new SmsConfigViewModelAssembler(configService, clients, activeProvider,
                mock(SmsQueueScheduler.class));
        SmsConfigViewModel initial = page.assemble(null, List.of());
        assertThat(initial.credentialFields()).as("the stub needs none").isEmpty();
        assertThat(initial.credentialGroups()).containsOnlyKeys("STUB", "CLOUDLI");
        assertThat(initial.credentialGroups().get("CLOUDLI"))
                .extracting(SmsConfigViewModel.CredentialField::name).containsExactly("api_user", "api_password");

        SmsConfigUpdateDto bare = settings(SmsProviderType.CLOUDLI, true, "", Map.of());
        List<String> errors = validate(bare);
        assertThat(errors).containsExactlyInAnyOrder(
                "sms.config.error.senderNumberRequired", "sms.config.error.credentialRequired");
        assertThat(page.assembleRejected(bare, errors).credentialFields()).containsExactly(
                new SmsConfigViewModel.CredentialField("api_user", "sms.test.fake.apiUser", true, false),
                new SmsConfigViewModel.CredentialField("api_password", "sms.test.fake.apiPassword", true, false));

        SmsConfigUpdateDto complete = completeFakeSettings();
        assertThat(validate(complete)).isEmpty();
        save(complete);

        SmsConfigViewModel saved = page.assemble(null, List.of());
        assertThat(saved.providerType()).isEqualTo("CLOUDLI");
        assertThat(saved.credentialFields()).extracting(SmsConfigViewModel.CredentialField::set)
                .containsExactly(true, true);
        assertThat(saved.toString()).doesNotContain("fake-password");
        assertThat(saved.errorKeys()).doesNotContain("sms.config.error.providerNotReady");
    }

    @Test
    @DisplayName("a direct send goes through the new provider with its settings and the number in E.164 form")
    void shouldSendThroughNewProvider_withItsSettings() {
        save(completeFakeSettings());

        SmsSendResultDto result = sendService().send(
                SmsSendCommand.patientMessage(123, "(416) 555-1212", "FAKE reminder", "999998"));

        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(fake.sentTo).as("the fake's own 10-digit format, from the E.164 number").containsExactly("4165551212");
        assertThat(fake.settingsSeen).singleElement().satisfies(settings -> {
            assertThat(settings.credential("api_user")).contains("fake-user");
            assertThat(settings.credential("api_password")).contains("fake-password");
            assertThat(settings.senderNumber()).contains(SENDER);
        });
        assertThat(transactions.transactions()).singleElement()
                .extracting(SmsTransaction::getProviderType, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsProviderType.CLOUDLI, "fake-sms-transaction-1");
        assertThat(stub.sends).isZero();
    }

    @Test
    @DisplayName("the queue sends through the active provider only, and fails what is left of the old one")
    void shouldDrainActiveProviderOnly_whenOldProvidersRowsAreQueued() {
        SmsTransaction left = transactions.addQueued(
                SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE queued before the swap", "999998"),
                SmsProviderType.STUB, CONSENTED);
        save(completeFakeSettings());
        SmsTransaction queued = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE queued after the swap", "999998"),
                SmsProviderType.CLOUDLI, CONSENTED);

        int processed = worker().processDueMessages(10);

        assertThat(processed).as("only sends count; failing the old provider's rows has a limit of its own")
                .isEqualTo(1);
        assertThat(queued).extracting(SmsTransaction::getStatus, SmsTransaction::getProviderMessageId)
                .containsExactly(SmsStatus.SENT, "fake-sms-transaction-2");
        assertThat(fake.sentTo).containsExactly("4165553434");
        assertThat(left).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        assertThat(stub.sends).as("never sent through the provider the clinic left").isZero();
    }

    @Test
    @DisplayName("swapping back hands the old provider nothing, and its queued texts fail instead of sending")
    void shouldHandOldProviderNothing_whenClinicSwapsBack() {
        save(completeFakeSettings());
        save(settings(SmsProviderType.STUB, true, "", Map.of()));
        SmsTransaction queued = transactions.addQueued(
                SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE", "999998"), SmsProviderType.CLOUDLI, CONSENTED);

        assertThat(configService.providerSettings(SmsProviderType.CLOUDLI).credential("api_user")).isEmpty();
        assertThat(savedRow.get().credentialNames()).as("cleared with the swap").isEmpty();

        worker().processDueMessages(10);

        assertThat(queued.getStatus()).isEqualTo(SmsStatus.FAILED);
        assertThat(fake.sentTo).isEmpty();
    }

    @Test
    @DisplayName("a credential is never carried over to the next provider, even under the same field name")
    void shouldNotCarryCredentialOver_whenNextProviderUsesSameFieldName() {
        install(stub, fake, new FakeSecondProviderClient(SmsProviderType.VOIPMS));
        save(completeFakeSettings());

        SmsConfigUpdateDto swap = settings(SmsProviderType.VOIPMS, true, SENDER, Map.of());
        assertThat(validate(swap)).as("the stored values belong to the old provider")
                .containsExactly("sms.config.error.credentialRequired");
        save(settings(SmsProviderType.VOIPMS, false, SENDER, Map.of()));

        SmsProviderSettings handedOver = configService.providerSettings(SmsProviderType.VOIPMS);
        assertThat(handedOver.credential("api_user")).isEmpty();
        assertThat(handedOver.credential("api_password")).isEmpty();
        assertThat(handedOver.senderNumber()).as("the clinic's own number is kept").contains(SENDER);
    }

    @Test
    @DisplayName("each provider is held to its own rate limit")
    void shouldHoldEachProvider_toItsOwnRateLimit() {
        Map<SmsProviderType, SmsProviderRateLimit> rows = new EnumMap<>(SmsProviderType.class);
        SmsProviderRateLimitDao dao = mock(SmsProviderRateLimitDao.class);
        when(dao.findByProviderTypeForUpdate(any())).thenAnswer(invocation -> Optional.of(rows.computeIfAbsent(
                invocation.getArgument(0), type -> SmsProviderRateLimit.forProvider(type, new Date()))));
        JpaSmsSendRateLimitService limiter = new JpaSmsSendRateLimitService(dao, clients);

        assertThat(List.of(limiter.tryAcquire(SmsProviderType.CLOUDLI), limiter.tryAcquire(SmsProviderType.CLOUDLI),
                limiter.tryAcquire(SmsProviderType.CLOUDLI))).as("the fake allows 2 a minute")
                .containsExactly(true, true, false);
        for (int i = 0; i < SmsSendRateLimit.DEFAULT.maxSends(); i++) {
            assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        }
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).as("the default, 5 per window").isFalse();
        for (int i = 0; i < SmsSendRateLimit.DEFAULT.maxSends(); i++) {
            assertThat(limiter.tryAcquire(SmsProviderType.VOIPMS)).as("no client installed: the default").isTrue();
        }
        assertThat(limiter.tryAcquire(SmsProviderType.VOIPMS)).isFalse();
    }

    @Test
    @DisplayName("a backlog left by the old provider never holds up the new provider's texts")
    void shouldSendNewProvidersText_whenOldProviderLeftABacklog() {
        List<SmsTransaction> backlog = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            backlog.add(transactions.addQueued(
                    SmsSendCommand.patientMessage(200 + i, "416-555-1212", "FAKE backlog", "999998"),
                    SmsProviderType.STUB, CONSENTED));
        }
        save(completeFakeSettings());
        SmsTransaction fresh = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE just queued", "999998"),
                SmsProviderType.CLOUDLI, CONSENTED);

        assertThat(worker().processDueMessages(1)).as("the quick wake-up after queueing one text").isEqualTo(1);

        assertThat(fresh.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(backlog).filteredOn(row -> row.getStatus() == SmsStatus.FAILED).hasSize(1);
        assertThat(stub.sends).isZero();
    }

    @Test
    @DisplayName("texts for a provider the clinic switches to during a run are sent, not failed")
    void shouldNotFailNewProvidersTexts_whenClinicSwitchesDuringRun() {
        save(completeFakeSettings());
        SmsTransaction queued = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE", "999998"), SmsProviderType.STUB, CONSENTED);
        SmsTransaction active = transactions.addQueued(
                SmsSendCommand.patientMessage(125, "416-555-5656", "FAKE active", "999998"),
                SmsProviderType.CLOUDLI, CONSENTED);
        transactions.afterProviderResult(row -> {
            if (row == active) {
                save(settings(SmsProviderType.STUB, true, "", Map.of()));
            }
        });
        SmsQueueProcessingService worker = worker();

        worker.processDueMessages(10);

        assertThat(queued.getStatus()).as("left for the next run, which sends it").isEqualTo(SmsStatus.QUEUED);
        assertThat(active.getStatus()).isEqualTo(SmsStatus.SENT);
    }

    @Test
    @DisplayName("a former provider's rows stop being failed as soon as the clinic chooses it again")
    void shouldStopFailingRows_whenClinicChoosesFormerProviderAgain() {
        save(completeFakeSettings());
        SmsTransaction first = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE one", "999998"), SmsProviderType.STUB, CONSENTED);
        SmsTransaction second = transactions.addQueued(
                SmsSendCommand.patientMessage(125, "416-555-5656", "FAKE two", "999998"), SmsProviderType.STUB, CONSENTED);
        transactions.afterProviderResult(row -> {
            if (row == first) {
                save(settings(SmsProviderType.STUB, true, "", Map.of()));
            }
        });
        SmsQueueProcessingService worker = worker();

        worker.processDueMessages(10);

        assertThat(first).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        assertThat(second.getStatus()).as("its provider is active again").isEqualTo(SmsStatus.QUEUED);
    }

    @Test
    @DisplayName("the system test still goes through the stub while the new provider is not ready")
    void shouldSendSystemTest_whenNewProviderIsNotReady() {
        save(settings(SmsProviderType.CLOUDLI, false, "", Map.of()));

        SmsSendResultDto result = sendService().sendSystemTest("416-555-1212", "999998", 1);

        assertThat(result.status()).isEqualTo(SmsStatus.SENT);
        assertThat(stub.sends).isEqualTo(1);
        assertThat(fake.settingsSeen).isEmpty();
    }

    @Test
    @DisplayName("a run stops sending through a provider the clinic leaves during the run")
    void shouldStopSending_whenClinicLeavesProviderDuringRun() {
        save(completeFakeSettings());
        SmsTransaction first = transactions.addQueued(
                SmsSendCommand.patientMessage(124, "416-555-3434", "FAKE one", "999998"), SmsProviderType.CLOUDLI,
                CONSENTED);
        SmsTransaction second = transactions.addQueued(
                SmsSendCommand.patientMessage(125, "416-555-5656", "FAKE two", "999998"), SmsProviderType.CLOUDLI,
                CONSENTED);
        transactions.afterProviderResult(row -> {
            if (row == first) {
                save(settings(SmsProviderType.STUB, true, "", Map.of()));
            }
        });
        SmsQueueProcessingService worker = worker();

        assertThat(worker.processDueMessages(10)).isEqualTo(1);

        assertThat(first.getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(second.getStatus()).as("not sent; the next run, with STUB active, fails it").isEqualTo(SmsStatus.QUEUED);
        assertThat(fake.sentTo).containsExactly("4165553434");
    }

    @Test
    @DisplayName("the new provider is not ready, and nothing is recorded, until its sender number is saved")
    void shouldRefuseSend_whenNewProviderLacksSenderNumber() {
        save(settings(SmsProviderType.CLOUDLI, false, "",
                Map.of("api_user", "fake-user", "api_password", "fake-password")));
        savedRow.get().setEnabled(true);

        SmsSendResultDto result = sendService().send(
                SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE", "999998"));

        assertThat(result.messages()).containsExactly(SmsSendService.SMS_PROVIDER_NOT_READY_MESSAGE);
        assertThat(transactions.transactions()).isEmpty();
        assertThat(fake.settingsSeen).isEmpty();
    }

    @Test
    @DisplayName("callbacks are checked and read by the new provider")
    void shouldCheckAndReadCallbacks_withNewProvider() {
        save(new SmsConfigUpdateDto(SmsProviderType.CLOUDLI, true, false, "613-555-0100", "callback-secret", false,
                Map.of("api_user", "fake-user", "api_password", "fake-password"), null));
        SmsWebhookService webhooks = new SmsWebhookService(clients, transactions, activeProvider, configService);

        assertThat(webhooks.processDeliveryWebhook(SmsProviderType.CLOUDLI, delivered("fake-1", "wrong"))).isEmpty();
        assertThat(webhooks.processInboundWebhook(SmsProviderType.CLOUDLI, delivered("fake-1", "callback-secret")))
                .as("a kind the provider does not send").isEmpty();
        assertThat(transactions.transactions()).as("a refused callback records nothing").isEmpty();

        assertThat(webhooks.processDeliveryWebhook(SmsProviderType.CLOUDLI, delivered("fake-1", "callback-secret")))
                .get().extracting(SmsTransaction::getProviderType, SmsTransaction::getStatus)
                .containsExactly(SmsProviderType.CLOUDLI, SmsStatus.DELIVERED);

        save(settings(SmsProviderType.STUB, true, "", Map.of()));
        assertThat(webhooks.processDeliveryWebhook(SmsProviderType.CLOUDLI, delivered("fake-2", "callback-secret")))
                .as("the clinic has left the provider").isEmpty();
    }

    /** A delivery report sent as a GET with everything in the address, the way VoIP.ms sends incoming texts. */
    private static SmsWebhookRequest delivered(String messageId, String token) {
        return new SmsWebhookRequest("GET", Map.of("id", List.of(messageId), "status", List.of("delivered"),
                "token", List.of(token)), Map.of(), "");
    }

    @Test
    void shouldReleaseQueueClaim_whenProviderChangesAfterClaim() {
        save(completeFakeSettings());
        SmsTransaction row = queueFakeText();
        transactions.afterNextQueueClaim(() -> save(settings(SmsProviderType.STUB, true, "", Map.of())));

        assertThat(worker().processDueMessages(10)).isZero();

        assertUnsentAndReleased(row);
    }

    @Test
    void shouldReleaseQueueClaim_whenCredentialsChangeAfterClaim() {
        save(completeFakeSettings());
        SmsTransaction row = queueFakeText();
        transactions.afterNextQueueClaim(this::rotateFakeCredentials);

        assertThat(worker().processDueMessages(10)).isZero();
        assertUnsentAndReleased(row);
        assertThat(worker().processDueMessages(10)).isEqualTo(1);
        assertThat(fake.settingsSeen.get(0).credential("api_password")).contains("fake-rotated-password");
    }

    @Test
    void shouldReleaseDirectClaim_whenProviderChangesAfterClaim() {
        save(completeFakeSettings());
        transactions.afterNextMarkSending(() -> save(settings(SmsProviderType.STUB, true, "", Map.of())));

        SmsSendResultDto result = sendService().send(fakeCommand());

        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertUnsentAndReleased(transactions.transactions().get(0));
    }

    @Test
    void shouldReleaseDirectClaim_whenCredentialsChangeAfterClaim() {
        save(completeFakeSettings());
        transactions.afterNextMarkSending(this::rotateFakeCredentials);

        SmsSendResultDto result = sendService().send(fakeCommand());

        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertUnsentAndReleased(transactions.transactions().get(0));
    }

    @Test
    void shouldReleaseQueueClaim_whenSettingsBecomeUnreadableAfterClaim() {
        save(completeFakeSettings());
        SmsTransaction row = queueFakeText();
        transactions.afterNextQueueClaim(() -> org.springframework.test.util.ReflectionTestUtils.setField(
                savedRow.get(), "credentialsJson", "{FAKE malformed"));

        assertThat(worker().processDueMessages(10)).isZero();
        assertUnsentAndReleased(row);
    }

    @Test
    void shouldReleaseDirectClaim_whenSettingsBecomeUnreadableAfterClaim() {
        save(completeFakeSettings());
        transactions.afterNextMarkSending(() -> org.springframework.test.util.ReflectionTestUtils.setField(
                savedRow.get(), "credentialsJson", "{FAKE malformed"));

        assertThat(sendService().send(fakeCommand()).status()).isEqualTo(SmsStatus.QUEUED);
        assertUnsentAndReleased(transactions.transactions().get(0));
    }

    @Test
    void shouldPreserveSendingState_whenReleaseLosesRaceAfterSettingsChange() {
        save(completeFakeSettings());
        SmsTransactionService recorder = org.mockito.Mockito.spy(transactions);
        doAnswer(invocation -> invocation.getArgument(0)).when(recorder).releaseClaim(any(), any());
        SmsSendService send = new SmsSendService(new SmsSendValidator(), command -> CONSENTED, clients, recorder,
                providerType -> {
                    rotateFakeCredentials();
                    return true;
                }, activeProvider, configService);

        SmsSendResultDto result = send.send(fakeCommand());

        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.accepted()).isFalse();
        assertThat(fake.settingsSeen).isEmpty();
    }

    @Test
    void shouldPropagateReleaseFailure_whenSettingsChangeBeforeDirectSend() {
        save(completeFakeSettings());
        SmsTransactionService recorder = org.mockito.Mockito.spy(transactions);
        org.mockito.Mockito.doThrow(new IllegalStateException("FAKE release failure"))
                .when(recorder).releaseClaim(any(), any());
        SmsSendService send = new SmsSendService(new SmsSendValidator(), command -> CONSENTED, clients, recorder,
                providerType -> {
                    rotateFakeCredentials();
                    return true;
                }, activeProvider, configService);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> send.send(fakeCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMS provider settings changed before dispatch");
        assertThat(fake.settingsSeen).isEmpty();
        assertThat(transactions.transactions().get(0).getStatus()).isEqualTo(SmsStatus.SENDING);
        org.mockito.Mockito.verify(recorder).releaseClaim(any(), any());
    }

    @Test
    void shouldFailQueuedSystemTest_withDistinctReasonWhenStubIsInactive() {
        SmsTransaction test = transactions.addQueued(new SmsSendCommand(null, "416-555-1212",
                SmsRecipientPhoneType.CELL, "FAKE system test", SmsMessagePurpose.SYSTEM_TEST,
                "999998", 1, null), SmsProviderType.STUB, CONSENTED);
        save(completeFakeSettings());

        assertThat(worker().processDueMessages(10)).isZero();

        assertThat(test.getStatus()).isEqualTo(SmsStatus.FAILED);
        assertThat(test.getErrorCode()).isEqualTo("QUEUE_SYSTEM_TEST_PROVIDER_CHANGED");
        assertThat(test.getErrorMessage()).contains("Send test");
        assertThat(stub.sends).isZero();
    }

    @Test
    void shouldBackOffFaultyInactiveRow_whenNextRowIsHealthy() {
        SmsTransaction first = transactions.addQueued(fakeCommand(), SmsProviderType.STUB, CONSENTED);
        SmsTransaction second = transactions.addQueued(fakeCommand(), SmsProviderType.STUB, CONSENTED);
        save(completeFakeSettings());
        SmsTransactionService recorder = org.mockito.Mockito.spy(transactions);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == first) {
                throw new IllegalStateException("FAKE failed write");
            }
            return invocation.callRealMethod();
        }).when(recorder).markProviderResult(any(), any());
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, clients, new SmsRetryCalculator(),
                providerType -> true, command -> CONSENTED, activeProvider, configService);
        Instant before = Instant.now();

        assertThat(worker.processDueMessages(10)).isZero();
        assertThat(worker.processDueMessages(10)).isZero();

        assertThat(first.getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(first.getAttemptCount()).isZero();
        assertThat(first.getNextAttemptAt().toInstant()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(5)));
        assertThat(second.getStatus()).isEqualTo(SmsStatus.FAILED);
    }

    @Test
    void shouldStopInactiveFailures_afterTwoFailedWritesInRun() {
        SmsTransaction first = transactions.addQueued(fakeCommand(), SmsProviderType.STUB, CONSENTED);
        SmsTransaction second = transactions.addQueued(fakeCommand(), SmsProviderType.STUB, CONSENTED);
        SmsTransaction third = transactions.addQueued(fakeCommand(), SmsProviderType.STUB, CONSENTED);
        save(completeFakeSettings());
        SmsTransactionService recorder = org.mockito.Mockito.spy(transactions);
        org.mockito.Mockito.doThrow(new IllegalStateException("FAKE failed write"))
                .when(recorder).markProviderResult(any(), any());
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, clients, new SmsRetryCalculator(),
                providerType -> true, command -> CONSENTED, activeProvider, configService);

        assertThat(worker.processDueMessages(10)).isZero();

        assertThat(first.getNextAttemptAt()).isAfter(new Date());
        assertThat(second.getNextAttemptAt()).isAfter(new Date());
        assertThat(third.getAttemptCount()).isZero();
        assertThat(third.getStatus()).isEqualTo(SmsStatus.QUEUED);
        org.mockito.Mockito.verify(recorder, org.mockito.Mockito.times(2)).markProviderResult(any(), any());
    }

    private void assertUnsentAndReleased(SmsTransaction row) {
        assertThat(row.getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(row.getAttemptCount()).isZero();
        assertThat(fake.settingsSeen).isEmpty();
        assertThat(stub.sends).isZero();
    }

    private SmsTransaction queueFakeText() {
        return transactions.addQueued(fakeCommand(), SmsProviderType.CLOUDLI, CONSENTED);
    }

    private SmsSendCommand fakeCommand() {
        return SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE", "999998");
    }

    private void rotateFakeCredentials() {
        save(settings(SmsProviderType.CLOUDLI, true, SENDER,
                Map.of("api_user", "fake-user", "api_password", "fake-rotated-password")));
    }

    private SmsSendService sendService() {
        return new SmsSendService(new SmsSendValidator(), command -> CONSENTED, clients, transactions,
                providerType -> true, activeProvider, configService);
    }

    private SmsQueueProcessingService worker() {
        return new SmsQueueProcessingService(transactions, clients, new SmsRetryCalculator(), providerType -> true,
                command -> CONSENTED, activeProvider, configService);
    }

    /** Builds the shared services on these installed providers; the saved settings row is kept. */
    private void install(SmsProviderClient... installed) {
        clients = new SmsProviderClientResolver(List.of(installed));
        SmsConfigDao dao = mock(SmsConfigDao.class);
        when(dao.findCurrent()).thenAnswer(invocation -> Optional.ofNullable(savedRow.get()));
        doAnswer(invocation -> {
            savedRow.set(invocation.getArgument(0));
            return null;
        }).when(dao).persist(any());
        configService = new SmsConfigService(dao, clients, mock(ApplicationEventPublisher.class),
                mock(SmsConfigAuditRecorder.class));
        activeProvider = new SmsDefaultProviderResolver(configService);
    }

    private List<String> validate(SmsConfigUpdateDto update) {
        return new SmsConfigValidator().validate(update, configService.installedProviders(),
                configService.providerNeeds(update));
    }

    private void save(SmsConfigUpdateDto update) {
        configService.save(update, "999998");
    }

    private static SmsConfigUpdateDto completeFakeSettings() {
        return settings(SmsProviderType.CLOUDLI, true, "613-555-0100",
                Map.of("api_user", "fake-user", "api_password", "fake-password"));
    }

    private static SmsConfigUpdateDto settings(SmsProviderType providerType, boolean enabled, String senderNumber,
                                               Map<String, String> credentials) {
        return new SmsConfigUpdateDto(providerType, enabled, false, senderNumber, "", false, credentials, null);
    }

    /** The test provider, counting the texts it is asked to send. */
    private static final class CountingStubProviderClient extends StubSmsProviderClient {
        private int sends;

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId,
                                             SmsProviderSettings settings) {
            sends++;
            return super.send(command, clientReferenceId, settings);
        }
    }

    /**
     * A second provider for tests only. It needs a user, a password and a sender number, allows 2 texts a minute,
     * wants 10-digit numbers, and sends delivery reports as a GET whose address carries the webhook secret.
     */
    private static final class FakeSecondProviderClient implements SmsProviderClient {
        private final SmsProviderType providerType;
        private final List<String> sentTo = new ArrayList<>();
        private final List<SmsProviderSettings> settingsSeen = new ArrayList<>();

        FakeSecondProviderClient() {
            this(SmsProviderType.CLOUDLI);
        }

        FakeSecondProviderClient(SmsProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public SmsProviderType providerType() {
            return providerType;
        }

        @Override
        public List<SmsCredentialField> credentialFields() {
            return List.of(new SmsCredentialField("api_user", "sms.test.fake.apiUser", true),
                    new SmsCredentialField("api_password", "sms.test.fake.apiPassword", true));
        }

        @Override
        public boolean requiresSenderNumber() {
            return true;
        }

        @Override
        public SmsSendRateLimit sendRateLimit() {
            return new SmsSendRateLimit(2, Duration.ofMinutes(1));
        }

        @Override
        public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId,
                                             SmsProviderSettings settings) {
            settingsSeen.add(settings);
            if (settings.credential("api_user").isEmpty() || settings.credential("api_password").isEmpty()
                    || settings.senderNumber().isEmpty()) {
                return SmsProviderSendResultDto.failed(SmsProviderErrorCode.NOT_CONFIGURED);
            }
            sentTo.add(command.recipientPhoneNumber().replaceFirst("^\\+1", ""));
            return SmsProviderSendResultDto.accepted("fake-" + clientReferenceId, SmsStatus.SENT);
        }

        @Override
        public boolean validateCallback(SmsWebhookRequest request, String webhookSecret,
                                        SmsProviderSettings settings) {
            if (webhookSecret == null || webhookSecret.isBlank() || request == null) {
                return false;
            }
            return request.queryParameter("token")
                    .map(token -> MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                            webhookSecret.getBytes(StandardCharsets.UTF_8)))
                    .orElse(false);
        }

        @Override
        public Set<SmsCallbackKind> acceptedCallbacks() {
            return Set.of(SmsCallbackKind.DELIVERY);
        }

        @Override
        public Optional<SmsInboundWebhookDto> parseInboundWebhook(SmsWebhookRequest request) {
            return Optional.empty();
        }

        @Override
        public Optional<SmsDeliveryWebhookDto> parseDeliveryWebhook(SmsWebhookRequest request) {
            if (request.queryParameter("status").filter("delivered"::equals).isEmpty()) {
                return Optional.empty();
            }
            return request.queryParameter("id").map(id -> new SmsDeliveryWebhookDto(providerType, id,
                    SmsStatus.DELIVERED, Instant.parse("2026-10-08T12:00:00Z"), null, null, Map.of()));
        }
    }
}
