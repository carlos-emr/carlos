/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.web;

import io.github.carlos_emr.carlos.lab.ca.all.web.ManualLabSubmissionReceipt.Outcome;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.*;

/** Session isolation, one-use delivery and bounded storage for manual-lab result notices. */
@Tag("unit")
@Tag("lab")
class ManualLabSubmissionReceiptUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldConsumeOnlyOnce_whenResultPageIsReloaded() {
        var session = new MockHttpSession();
        String id = ManualLabSubmissionReceipt.save(session, Outcome.STORED);
        assertThat(ManualLabSubmissionReceipt.consume(session, id)).isEqualTo(Outcome.STORED);
        assertThat(ManualLabSubmissionReceipt.consume(session, id)).isNull();
        assertThat(session.getAttributeNames().hasMoreElements()).isFalse();
    }

    @Test
    void shouldKeepNoticesSeparate_whenLabWindowsFinishOutOfOrder() {
        var session = new MockHttpSession();
        String stored = ManualLabSubmissionReceipt.save(session, Outcome.STORED);
        String duplicate = ManualLabSubmissionReceipt.save(session, Outcome.ALREADY_RECORDED);
        assertThat(stored).isNotEqualTo(duplicate);
        assertThat(ManualLabSubmissionReceipt.consume(session, duplicate)).isEqualTo(Outcome.ALREADY_RECORDED);
        assertThat(ManualLabSubmissionReceipt.consume(session, stored)).isEqualTo(Outcome.STORED);
    }

    @Test
    void shouldKeepReceiptPrivate_whenAnotherSessionOrUnknownIdIsUsed() {
        var session = new MockHttpSession();
        String id = ManualLabSubmissionReceipt.save(session, Outcome.STORED);
        assertThat(ManualLabSubmissionReceipt.consume(new MockHttpSession(), id)).isNull();
        assertThat(ManualLabSubmissionReceipt.consume(null, id)).isNull();
        assertThat(ManualLabSubmissionReceipt.consume(session, null)).isNull();
        assertThat(ManualLabSubmissionReceipt.consume(session, "STORED")).isNull();
        assertThat(ManualLabSubmissionReceipt.consume(session, id)).isEqualTo(Outcome.STORED);
    }

    @Test
    void shouldRejectReceipt_whenOutcomeIsMissing() {
        var session = new MockHttpSession();
        assertThatIllegalArgumentException().isThrownBy(
                () -> ManualLabSubmissionReceipt.save(session, null));
        assertThat(session.getAttributeNames().hasMoreElements()).isFalse();
    }

    @Test
    void shouldPreserveUncertainty_whenStorageCouldNotBeConfirmed() {
        var session = new MockHttpSession();
        String id = ManualLabSubmissionReceipt.save(session, Outcome.UNKNOWN);
        assertThat(ManualLabSubmissionReceipt.consume(session, id)).isEqualTo(Outcome.UNKNOWN);
        assertThat(ManualLabSubmissionReceipt.consume(session, id)).isNull();
    }

    @Test
    void shouldBoundAbandonedReceipts_whenRedirectsAreNeverFollowed() {
        var session = new MockHttpSession();
        var ids = new ArrayList<String>();
        for (int i = 0; i < 65; i++) {
            ids.add(ManualLabSubmissionReceipt.save(session, Outcome.STORED));
        }
        assertThat(ManualLabSubmissionReceipt.consume(session, ids.getFirst())).isNull();
        for (String id : ids.subList(1, ids.size())) {
            assertThat(ManualLabSubmissionReceipt.consume(session, id)).isEqualTo(Outcome.STORED);
        }
        assertThat(session.getAttributeNames().hasMoreElements()).isFalse();
    }

    @Test
    void shouldDeliverEachReceiptOnce_whenRequestsOverlap() throws Exception {
        var session = new MockHttpSession();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var saves = new ArrayList<Callable<String>>();
            for (int i = 0; i < 12; i++) {
                saves.add(() -> ManualLabSubmissionReceipt.save(session, Outcome.STORED));
            }
            var ids = new ArrayList<String>();
            for (var result : executor.invokeAll(saves)) {
                ids.add(result.get());
            }
            assertThat(ids).doesNotHaveDuplicates();
            for (String id : ids) {
                Callable<Outcome> consume = () -> ManualLabSubmissionReceipt.consume(session, id);
                var results = executor.invokeAll(List.of(consume, consume));
                assertThat(new Outcome[] {results.get(0).get(), results.get(1).get()})
                        .containsExactlyInAnyOrder(Outcome.STORED, null);
            }
        }
    }
}
