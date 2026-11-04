/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Attempt;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Claim;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Scope;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard.Verdict;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FormSubmissionGuard}: one rendered create form saves once (issue #4225),
 * without suppressing a retry after a save that wrote nothing or a later, newly rendered form.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("security")
@DisplayName("FormSubmissionGuard")
class FormSubmissionGuardUnitTest {

    private final MockHttpSession session = new MockHttpSession();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Runs storageStarted() inside a synchronization scope and completes it with {@code status}. */
    private static void storeAndComplete(Claim claim, int status) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            claim.storageStarted();
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(status);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Attempt attempt(String token, String binding) {
        return FormSubmissionGuard.attempt(session, token, Scope.BILLING_ON_SAVE, binding, 0);
    }

    @Test
    void shouldClaimAnIssuedToken_forFirstSubmission() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");

        Attempt attempt = attempt(token, "42");

        assertThat(attempt.verdict()).isEqualTo(Verdict.PROCEED);
        assertThat(attempt.claim()).isNotNull();
    }

    @Test
    void shouldAnswerRepeatWithFirstResult_whenFirstSaveCommitted() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.saved("9001");
        claim.close();

        Attempt repeat = attempt(token, "42");

        assertThat(repeat.verdict()).isEqualTo(Verdict.ALREADY_SAVED);
        assertThat(repeat.result()).isEqualTo("9001");
        assertThat(repeat.claim()).isNull();
    }

    @Test
    void shouldAnswerRepeatAsSaved_whenSaveRecordedWithoutTransaction() {
        String token = FormSubmissionGuard.issue(session, Scope.DEMOGRAPHIC_ADD, null);
        Claim claim = FormSubmissionGuard.attempt(session, token, Scope.DEMOGRAPHIC_ADD, null, 0).claim();
        claim.saved("123");
        claim.close();

        Attempt repeat = FormSubmissionGuard.attempt(session, token, Scope.DEMOGRAPHIC_ADD, null, 0);

        assertThat(repeat.verdict()).isEqualTo(Verdict.ALREADY_SAVED);
        assertThat(repeat.result()).isEqualTo("123");
    }

    @Test
    void shouldRefuseRepeatAsUnconfirmed_whenCommitOutcomeIsUnknown() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_UNKNOWN);
        claim.close();

        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.UNCONFIRMED);
    }

    @Test
    void shouldRefuseRepeatAsUnconfirmed_whenCommittedButNoResultRecorded() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.close();

        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.UNCONFIRMED);
    }

    @Test
    void shouldNeverFreeTheToken_whenStorageStartedOutsideTransaction() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        claim.storageStarted();
        claim.close();

        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.UNCONFIRMED);
    }

    @Test
    void shouldFreeTheTokenForRetry_whenFirstSaveRolledBack() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_ROLLED_BACK);
        claim.close();

        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldFreeTheTokenForRetry_whenStorageNeverStarted() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        attempt(token, "42").claim().close();

        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldKeepTheRecordedOutcome_whenClaimIsClosedTwice() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim first = attempt(token, "42").claim();
        first.close();
        Claim retry = attempt(token, "42").claim();
        retry.saved("7");
        retry.close();

        first.close();

        assertThat(attempt(token, "42").result()).isEqualTo("7");
    }

    @Test
    void shouldCloseQuietly_whenTheSessionEndedDuringTheSave() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim claim = attempt(token, "42").claim();
        claim.saved("9001");
        session.invalidate();

        claim.close();

        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldReportAbsent_forBlankToken(String token) {
        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.ABSENT);
        assertThat(attempt(null, "42").verdict()).isEqualTo(Verdict.ABSENT);
    }

    @Test
    void shouldRefuseAsStale_forMalformedOrUnknownToken() {
        assertThat(attempt("not-a-token", "42").verdict()).isEqualTo(Verdict.STALE);
        assertThat(attempt("00000000-0000-0000-0000-000000000000", "42").verdict()).isEqualTo(Verdict.STALE);
    }

    @Test
    void shouldRefuseAsStale_whenTokenIsPresentedForAnotherBinding() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");

        assertThat(attempt(token, "43").verdict()).isEqualTo(Verdict.STALE);
        assertThat(attempt(token, "42").verdict()).isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldRefuseAsStale_whenTokenIsPresentedForAnotherForm() {
        String token = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);

        assertThat(FormSubmissionGuard.attempt(session, token, Scope.DEMOGRAPHIC_ADD, null, 0).verdict())
                .isEqualTo(Verdict.STALE);
    }

    @Test
    void shouldRefuseAsStale_whenTokenWasIssuedToAnotherSession() {
        String token = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);

        assertThat(FormSubmissionGuard.attempt(new MockHttpSession(), token, Scope.APPOINTMENT_ADD, null, 0).verdict())
                .isEqualTo(Verdict.STALE);
    }

    @Test
    void shouldIssueIndependentTokens_forEachRendering() {
        String first = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        String second = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        Claim claim = FormSubmissionGuard.attempt(session, first, Scope.APPOINTMENT_ADD, null, 0).claim();
        claim.saved("1");
        claim.close();

        assertThat(second).isNotEqualTo(first);
        assertThat(FormSubmissionGuard.attempt(session, second, Scope.APPOINTMENT_ADD, null, 0).verdict())
                .isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldReportInProgress_whenFirstSaveOutlastsTheWait() {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim first = attempt(token, "42").claim();

        Attempt repeat = FormSubmissionGuard.attempt(session, token, Scope.BILLING_ON_SAVE, "42", 150);

        assertThat(repeat.verdict()).isEqualTo(Verdict.IN_PROGRESS);
        first.close();
    }

    @Test
    void shouldWaitForTheFirstSave_andAnswerItsResult() throws Exception {
        String token = FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, "42");
        Claim first = attempt(token, "42").claim();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch waiting = new CountDownLatch(1);
            CompletableFuture<Attempt> repeat = CompletableFuture.supplyAsync(() -> {
                waiting.countDown();
                return FormSubmissionGuard.attempt(session, token, Scope.BILLING_ON_SAVE, "42", 10_000);
            }, executor);
            assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(250);
            first.saved("555");
            first.close();

            Attempt answered = repeat.get(10, TimeUnit.SECONDS);
            assertThat(answered.verdict()).isEqualTo(Verdict.ALREADY_SAVED);
            assertThat(answered.result()).isEqualTo("555");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldLetOnlyOneSubmissionProceed_whenManyArriveTogether() throws Exception {
        String token = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        int submissions = 8;
        ExecutorService executor = Executors.newFixedThreadPool(submissions);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Attempt>> attempts = new ArrayList<>();
            for (int i = 0; i < submissions; i++) {
                attempts.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    Attempt attempt = FormSubmissionGuard.attempt(session, token, Scope.APPOINTMENT_ADD, null, 0);
                    if (attempt.claim() != null) {
                        attempt.claim().saved("1");
                        attempt.claim().close();
                    }
                    return attempt;
                }, executor));
            }
            start.countDown();
            long proceeded = 0;
            for (CompletableFuture<Attempt> attempt : attempts) {
                Verdict verdict = attempt.get(10, TimeUnit.SECONDS).verdict();
                assertThat(verdict).isIn(Verdict.PROCEED, Verdict.ALREADY_SAVED, Verdict.IN_PROGRESS);
                if (verdict == Verdict.PROCEED) proceeded++;
            }
            assertThat(proceeded).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldBoundEachFormSeparately_soOneFormCannotEvictAnothersTokens() {
        String appointment = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        for (int i = 0; i < FormSubmissionGuard.MAX_PENDING_PER_SCOPE + 10; i++) {
            FormSubmissionGuard.issue(session, Scope.BILLING_ON_SAVE, String.valueOf(i));
        }

        assertThat(FormSubmissionGuard.attempt(session, appointment, Scope.APPOINTMENT_ADD, null, 0).verdict())
                .isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldEvictFinishedTokensFirst_whenAFormReachesItsBound() {
        String saved = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        Claim claim = FormSubmissionGuard.attempt(session, saved, Scope.APPOINTMENT_ADD, null, 0).claim();
        claim.saved("1");
        claim.close();
        String open = FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        for (int i = 0; i < FormSubmissionGuard.MAX_PENDING_PER_SCOPE - 2; i++) {
            FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);
        }

        FormSubmissionGuard.issue(session, Scope.APPOINTMENT_ADD, null);

        // The finished token made room; the oldest unsubmitted form is still submittable.
        assertThat(FormSubmissionGuard.attempt(session, saved, Scope.APPOINTMENT_ADD, null, 0).verdict())
                .isEqualTo(Verdict.STALE);
        assertThat(FormSubmissionGuard.attempt(session, open, Scope.APPOINTMENT_ADD, null, 0).verdict())
                .isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldAnswerRefusals_withConflictAndNoStore() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = FormSubmissionGuard.refuse(request, response, Verdict.IN_PROGRESS);

        assertThat(result).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getAttribute(FormSubmissionGuard.MESSAGE_KEY_ATTRIBUTE))
                .isEqualTo("global.formSubmission.inProgress");
    }

    @Test
    void shouldNameALocalizedMessage_forEveryRefusal() {
        assertThat(FormSubmissionGuard.messageKey(Verdict.UNCONFIRMED)).isEqualTo("global.formSubmission.unconfirmed");
        assertThat(FormSubmissionGuard.messageKey(Verdict.STALE)).isEqualTo("global.formSubmission.stale");
        java.util.ResourceBundle english = java.util.ResourceBundle.getBundle("oscarResources", java.util.Locale.ENGLISH);
        for (Verdict verdict : List.of(Verdict.IN_PROGRESS, Verdict.UNCONFIRMED, Verdict.STALE)) {
            assertThat(english.getString(FormSubmissionGuard.messageKey(verdict))).isNotBlank();
        }
    }
}
