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
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.*;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

/** Exercises independent windows, replay rejection and bounded session persistence. */
@Tag("unit")
@Tag("eform")
class EFormSubmissionGuardUnitTest extends CarlosUnitTestBase {
    private final MockHttpSession session = new MockHttpSession();

    private String issue() {
        return EFormSubmissionGuard.issue(session, "1", "123");
    }

    @Test
    void shouldRejectReplay_whenStorageOutcomeIsUnknown() {
        String token = issue();
        try (var claim = EFormSubmissionGuard.attempt(session, token, "1", "123").claim()) {
            assertThat(claim).isNotNull();
            claim.storageStarted();
            for (int i = 0; i < 64; i++) issue();
        }
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
    }

    @Test
    void shouldPermitRetry_whenValidationStopsBeforeStorage() {
        String token = issue();
        try (var claim = EFormSubmissionGuard.attempt(session, token, "1", "123").claim()) {
            assertThat(claim).isNotNull();
        }
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNotNull();
    }

    @Test
    void shouldRestoreRetry_whenPreparationClaimIsEvicted() {
        String token = issue();
        String oldestNewView;
        try (var claim = EFormSubmissionGuard.attempt(session, token, "1", "123").claim()) {
            assertThat(claim).isNotNull();
            oldestNewView = issue();
            for (int i = 0; i < 63; i++) issue();
            assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
        }
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNotNull();
        // Restoring this retry must still respect the bounded set of editing opportunities.
        assertThat(EFormSubmissionGuard.attempt(session, oldestNewView, "1", "123").claim()).isNull();
    }

    @Test
    void shouldNotReleaseNewAttempt_whenAnOldClaimIsClosedAgain() {
        String token = issue();
        var first = EFormSubmissionGuard.attempt(session, token, "1", "123").claim();
        first.close();
        try (var retry = EFormSubmissionGuard.attempt(session, token, "1", "123").claim()) {
            assertThat(retry).isNotNull();
            first.close();
            assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
            retry.storageStarted();
        }
    }

    @Test
    void shouldRejectForeignOrMissingIdentity_withoutConsumingValidToken() {
        String token = issue();
        assertThat(EFormSubmissionGuard.attempt(session, null, "1", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(session, UUID.randomUUID().toString(), "1", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(session, token, "2", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "456").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(new MockHttpSession(), token, "1", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNotNull();
    }

    @Test
    void shouldAllowIndependentWindows_whenTemplateAndPatientMatch() {
        String first = issue();
        String second = issue();
        assertThat(second).isNotEqualTo(first);
        assertThat(EFormSubmissionGuard.attempt(session, first, "1", "123").claim()).isNotNull();
        assertThat(EFormSubmissionGuard.attempt(session, second, "1", "123").claim()).isNotNull();
    }

    @Test
    void shouldRejectOldPage_whenBoundedSessionEvictsItsIdentity() {
        String old = issue();
        String recent = null;
        for (int i = 0; i < 64; i++) recent = issue();
        assertThat(EFormSubmissionGuard.attempt(session, old, "1", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(session, recent, "1", "123").claim()).isNotNull();
    }

    @Test
    void shouldPreserveOpenDraft_whenConsumedIdentitiesCanBeEvicted() {
        String draft = issue();
        String consumed = issue();
        try (var claim = EFormSubmissionGuard.attempt(session, consumed, "1", "123").claim()) {
            claim.storageStarted();
        }
        for (int i = 0; i < 63; i++) issue();
        assertThat(EFormSubmissionGuard.attempt(session, draft, "1", "123").claim()).isNotNull();
        var evicted = EFormSubmissionGuard.attempt(session, consumed, "1", "123");
        assertThat(evicted.claim()).isNull();
        // An unknown identity can represent an old completed save. Never claim that no earlier save occurred.
        assertThat(evicted.stale()).isTrue();
    }

    @Test
    void shouldDistinguishUnavailableIdentity_fromKnownReplay() {
        String token = issue();
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNotNull();
        var replay = EFormSubmissionGuard.attempt(session, token, "1", "123");
        assertThat(replay.claim()).isNull();
        assertThat(replay.stale()).isFalse();
        assertThat(EFormSubmissionGuard.attempt(session, null, "1", "123").stale()).isTrue();
    }

    @Test
    void shouldReserveExactlyOnce_whenRequestsRace() throws Exception {
        String token = issue();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> submit = () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                var claim = EFormSubmissionGuard.attempt(session, token, "1", "123").claim();
                if (claim == null) return false;
                try (claim) { claim.storageStarted(); }
                return true;
            };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void shouldPreserveConsumedState_afterSessionSerialization() throws Exception {
        String consumed = issue();
        String available = issue();
        try (var claim = EFormSubmissionGuard.attempt(session, consumed, "1", "123").claim()) {
            claim.storageStarted();
        }
        var restored = new MockHttpSession();
        for (String key : Collections.list(session.getAttributeNames())) {
            var bytes = new ByteArrayOutputStream();
            try (var output = new ObjectOutputStream(bytes)) { output.writeObject(session.getAttribute(key)); }
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                restored.setAttribute(key, input.readObject());
            }
        }
        assertThat(EFormSubmissionGuard.attempt(restored, consumed, "1", "123").claim()).isNull();
        assertThat(EFormSubmissionGuard.attempt(restored, available, "1", "123").claim()).isNotNull();
    }

    @Test
    void shouldReplaceIdentityInsideForm_whenSavedMarkupAlreadyContainsOne() {
        EForm form = new EForm();
        form.setFormHtml("<form action='addEForm'><input name='carlosEformSubmission' value='old'>"
                + "<input name='newForm' value='false'><input name='subject' value='kept'></form>");
        String token = issue();
        form.setSubmissionToken(token);
        var html = Jsoup.parse(form.getFormHtml());
        assertThat(html.select("[name=carlosEformSubmission]")).hasSize(1);
        assertThat(html.selectFirst("form input[name=carlosEformSubmission]").val()).isEqualTo(token);
        assertThat(html.selectFirst("[name=newForm]").val()).isEqualTo("false");
        assertThat(html.selectFirst("[name=subject]").val()).isEqualTo("kept");
    }
}
