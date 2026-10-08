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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AllergySaveTokens Unit Tests")
@Tag("unit")
@Tag("rx")
class AllergySaveTokensUnitTest {

    private static final String TOKEN = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String FP = AllergySaveTokens.fingerprint("a", "b");

    @Test
    void shouldClaimThenReportAlreadySaved_afterMarkSaved() {
        MockHttpSession session = new MockHttpSession();
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.CLAIMED);
        AllergySaveTokens.markSaved(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.ALREADY_SAVED);
    }

    @Test
    void shouldReportInProgress_whenClaimedTwiceConcurrently() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<AllergySaveTokens.Claim> racer = () -> {
                start.await();
                return AllergySaveTokens.claim(session, TOKEN, FP);
            };
            Future<AllergySaveTokens.Claim> first = executor.submit(racer);
            Future<AllergySaveTokens.Claim> second = executor.submit(racer);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(
                    AllergySaveTokens.Claim.CLAIMED, AllergySaveTokens.Claim.IN_PROGRESS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowReclaim_afterRelease() {
        MockHttpSession session = new MockHttpSession();
        AllergySaveTokens.claim(session, TOKEN, FP);
        AllergySaveTokens.release(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.CLAIMED);
    }

    @Test
    void shouldNotReleaseSavedToken_forReleaseAfterSuccess() {
        MockHttpSession session = new MockHttpSession();
        AllergySaveTokens.claim(session, TOKEN, FP);
        AllergySaveTokens.markSaved(session, TOKEN);
        AllergySaveTokens.release(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.ALREADY_SAVED);
    }

    @Test
    void shouldScopeTokensToSession_perSession() {
        MockHttpSession other = new MockHttpSession();
        AllergySaveTokens.claim(other, TOKEN, FP);
        AllergySaveTokens.markSaved(other, TOKEN);
        assertThat(AllergySaveTokens.claim(new MockHttpSession(), TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.CLAIMED);
    }

    @Test
    void shouldValidateFormat_forTokens() {
        assertThat(AllergySaveTokens.isAcceptable(null)).isTrue();
        assertThat(AllergySaveTokens.isAcceptable("")).isTrue();
        assertThat(AllergySaveTokens.isAcceptable(TOKEN)).isTrue();
        assertThat(AllergySaveTokens.isAcceptable("short")).isFalse();
        assertThat(AllergySaveTokens.isAcceptable(TOKEN + "<")).isFalse();
    }

    @Test
    void shouldResumeArchive_whenAddedButNotSaved() {
        MockHttpSession session = new MockHttpSession();
        AllergySaveTokens.claim(session, TOKEN, FP);
        AllergySaveTokens.markAdded(session, TOKEN);
        AllergySaveTokens.release(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.RESUME_ARCHIVE);
        AllergySaveTokens.release(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, FP)).isEqualTo(AllergySaveTokens.Claim.RESUME_ARCHIVE);
    }

    @Test
    void shouldReportMismatch_whenValuesChangedForUsedToken() {
        MockHttpSession session = new MockHttpSession();
        AllergySaveTokens.claim(session, TOKEN, FP);
        AllergySaveTokens.markSaved(session, TOKEN);
        assertThat(AllergySaveTokens.claim(session, TOKEN, AllergySaveTokens.fingerprint("a", "changed")))
                .isEqualTo(AllergySaveTokens.Claim.PAYLOAD_MISMATCH);
    }

    @Test
    void shouldDistinguishFingerprints_forShiftedBoundaries() {
        assertThat(AllergySaveTokens.fingerprint("ab", "c")).isNotEqualTo(AllergySaveTokens.fingerprint("a", "bc"));
        assertThat(AllergySaveTokens.fingerprint((String) null)).isNotEqualTo(AllergySaveTokens.fingerprint("null"));
    }
}
