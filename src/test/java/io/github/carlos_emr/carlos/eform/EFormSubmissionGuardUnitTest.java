/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
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
        try (var claim = EFormSubmissionGuard.claim(session, token, "1", "123")) {
            assertThat(claim).isNotNull();
            claim.storageStarted();
        }
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "123")).isNull();
    }

    @Test
    void shouldPermitRetry_whenValidationStopsBeforeStorage() {
        String token = issue();
        try (var claim = EFormSubmissionGuard.claim(session, token, "1", "123")) {
            assertThat(claim).isNotNull();
        }
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "123")).isNotNull();
    }

    @Test
    void shouldRejectForeignOrMissingIdentity_withoutConsumingValidToken() {
        String token = issue();
        assertThat(EFormSubmissionGuard.claim(session, null, "1", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(session, UUID.randomUUID().toString(), "1", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(session, token, "2", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "456")).isNull();
        assertThat(EFormSubmissionGuard.claim(new MockHttpSession(), token, "1", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "123")).isNotNull();
    }

    @Test
    void shouldAllowIndependentWindows_whenTemplateAndPatientMatch() {
        String first = issue();
        String second = issue();
        assertThat(second).isNotEqualTo(first);
        assertThat(EFormSubmissionGuard.claim(session, first, "1", "123")).isNotNull();
        assertThat(EFormSubmissionGuard.claim(session, second, "1", "123")).isNotNull();
    }

    @Test
    void shouldRejectOldPage_whenBoundedSessionEvictsItsIdentity() {
        String old = issue();
        String recent = null;
        for (int i = 0; i < 64; i++) recent = issue();
        assertThat(EFormSubmissionGuard.claim(session, old, "1", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(session, recent, "1", "123")).isNotNull();
    }

    @Test
    void shouldReserveExactlyOnce_whenRequestsRace() throws Exception {
        String token = issue();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> submit = () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                var claim = EFormSubmissionGuard.claim(session, token, "1", "123");
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
        try (var claim = EFormSubmissionGuard.claim(session, consumed, "1", "123")) {
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
        assertThat(EFormSubmissionGuard.claim(restored, consumed, "1", "123")).isNull();
        assertThat(EFormSubmissionGuard.claim(restored, available, "1", "123")).isNotNull();
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
