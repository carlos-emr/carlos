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
package io.github.carlos_emr.carlos.prevention;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.prevention.PreventionSubmissionGuard.Attempt;
import io.github.carlos_emr.carlos.prevention.PreventionSubmissionGuard.Claim;
import io.github.carlos_emr.carlos.prevention.PreventionSubmissionGuard.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PreventionSubmissionGuard}: one rendered prevention form saves once
 * (issue #4410), without suppressing a retry after a save that wrote nothing.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("PreventionSubmissionGuard")
class PreventionSubmissionGuardUnitTest {

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

    @Test
    void shouldClaimAnIssuedToken_forFirstSubmission() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);

        Attempt attempt = PreventionSubmissionGuard.attempt(session, token, "42", "", 0);

        assertThat(attempt.verdict()).isEqualTo(Verdict.PROCEED);
        assertThat(attempt.claim()).isNotNull();
    }

    @Test
    void shouldAnswerRepeatAsSaved_whenFirstSaveCommitted() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.close();

        Attempt repeat = PreventionSubmissionGuard.attempt(session, token, "42", null, 0);

        assertThat(repeat.verdict()).isEqualTo(Verdict.ALREADY_SAVED);
        assertThat(repeat.claim()).isNull();
    }

    @Test
    void shouldTreatUnknownCommitOutcomeAsSaved_soItIsNeverWrittenTwice() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_UNKNOWN);
        claim.close();

        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", null, 0).verdict())
                .isEqualTo(Verdict.ALREADY_SAVED);
    }

    @Test
    void shouldFreeTheTokenForRetry_whenFirstSaveRolledBack() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_ROLLED_BACK);
        claim.close();

        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", null, 0).verdict())
                .isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldFreeTheTokenForRetry_whenStorageNeverStarted() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim().close();

        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", null, 0).verdict())
                .isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldReportInProgress_whenFirstSaveOutlastsTheWait() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        PreventionSubmissionGuard.attempt(session, token, "42", null, 0);

        Attempt repeat = PreventionSubmissionGuard.attempt(session, token, "42", null, 150);

        assertThat(repeat.verdict()).isEqualTo(Verdict.IN_PROGRESS);
    }

    @Test
    void shouldWaitForTheFirstSave_andAnswerTheRepeatFromItsOutcome() throws Exception {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim();

        CompletableFuture<Attempt> repeat = CompletableFuture.supplyAsync(
                () -> PreventionSubmissionGuard.attempt(session, token, "42", null, 10_000));
        Thread.sleep(250);
        assertThat(repeat).isNotDone();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.close();

        assertThat(repeat.get(5, TimeUnit.SECONDS).verdict()).isEqualTo(Verdict.ALREADY_SAVED);
    }

    @Test
    void shouldRefuseAsStale_forTokensThisSessionDidNotIssueForThisPatientAndRecord() {
        String token = PreventionSubmissionGuard.issue(session, "42", "100");

        assertThat(PreventionSubmissionGuard.attempt(session, token, "43", "100", 0).verdict()).isEqualTo(Verdict.STALE);
        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", "101", 0).verdict()).isEqualTo(Verdict.STALE);
        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", null, 0).verdict()).isEqualTo(Verdict.STALE);
        assertThat(PreventionSubmissionGuard.attempt(new MockHttpSession(), token, "42", "100", 0).verdict())
                .isEqualTo(Verdict.STALE);
        assertThat(PreventionSubmissionGuard.attempt(session, "", "42", "100", 0).verdict()).isEqualTo(Verdict.STALE);
        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", "100", 0).verdict()).isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldGiveEachRenderingItsOwnToken_soALaterIntentionalAddIsNotSuppressed() {
        String first = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, first, "42", null, 0).claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.close();

        String second = PreventionSubmissionGuard.issue(session, "42", null);

        assertThat(second).isNotEqualTo(first);
        assertThat(PreventionSubmissionGuard.attempt(session, second, "42", null, 0).verdict()).isEqualTo(Verdict.PROCEED);
    }

    @Test
    void shouldKeepAnInFlightSave_whenManyLaterFormsAreOpened() {
        String saving = PreventionSubmissionGuard.issue(session, "42", null);
        PreventionSubmissionGuard.attempt(session, saving, "42", null, 0);
        for (int i = 0; i < 200; i++) {
            PreventionSubmissionGuard.issue(session, "42", null);
        }

        assertThat(PreventionSubmissionGuard.attempt(session, saving, "42", null, 0).verdict())
                .isEqualTo(Verdict.IN_PROGRESS);
    }

    @Test
    void shouldReportTheSavedRecord_toARepeatOfACommittedSave() {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        Claim claim = PreventionSubmissionGuard.attempt(session, token, "42", null, 0).claim();
        storeAndComplete(claim, TransactionSynchronization.STATUS_COMMITTED);
        claim.saved(100);
        claim.close();

        assertThat(PreventionSubmissionGuard.attempt(session, token, "42", null, 0).savedId()).isEqualTo(100);
    }

    @Test
    void shouldLetExactlyOneOfManySimultaneousSubmissionsClaimTheToken() throws Exception {
        String token = PreventionSubmissionGuard.issue(session, "42", null);
        int requests = 8;
        CyclicBarrier claimBoundary = new CyclicBarrier(requests);
        // One thread per request: the common pool may have fewer threads than the barrier needs.
        ExecutorService threads = Executors.newFixedThreadPool(requests);
        List<CompletableFuture<Verdict>> verdicts = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            verdicts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    claimBoundary.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                // No wait: whoever does not claim sees the first save in flight.
                return PreventionSubmissionGuard.attempt(session, token, "42", null, 0).verdict();
            }, threads));
        }

        List<Verdict> outcomes = new ArrayList<>();
        try {
            for (CompletableFuture<Verdict> verdict : verdicts) {
                outcomes.add(verdict.get(10, TimeUnit.SECONDS));
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(outcomes).filteredOn(v -> v == Verdict.PROCEED).hasSize(1);
        assertThat(outcomes).filteredOn(v -> v == Verdict.IN_PROGRESS).hasSize(requests - 1);
    }
}
