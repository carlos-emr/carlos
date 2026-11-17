/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService.Operation.PREVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
class EFormRenderApprovalCapacityUnitTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
    private final EFormRenderApprovalService service = new EFormRenderApprovalService(new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    });
    private final MockHttpServletRequest request = request();
    private final LoggedInInfo user = user("999998");
    private final EFormRenderCompletenessReport report =
            new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false);

    @Test
    void rotatesConsumedTicketOnceWithoutChangingExactOmissions() {
        String original = service.issue(request, user, 42, "123", PREVIEW, report);
        EFormRenderApproval consumed = service.consume(request, user, 42, "123", PREVIEW, original);
        String replacement = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consumed);
        assertThat(replacement).isNotBlank().isNotEqualTo(original);
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consumed)).isNull();
        assertThat(service.consume(request, user, 42, "123", PREVIEW, original)).isNull();
        EFormRenderApproval retry = service.consume(request, user, 42, "123", PREVIEW, replacement);
        assertThat(retry.permits(42, "999998", report)).isTrue();
        assertThat(retry.permits(42, "999998",
                new EFormRenderCompletenessReport(2, 0, 0, 0, false, false, false, false))).isFalse();
        assertThat(retry.permits(43, "999998", report)).isFalse();
        assertThat(service.consume(request, user, 42, "123", PREVIEW, replacement)).isNull();
    }

    @Test
    void expiryDuringClientBackoffDropsAllConsentWithoutFailingTheCapacityReceipt() {
        Instant expires = now.get().plusSeconds(120);
        EFormRenderApproval first = consume();
        now.set(now.get().plusSeconds(80));
        String replacement = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, first);
        EFormRenderApproval second = service.consume(request, user, 42, "123", PREVIEW, replacement);
        assertThat(second.expiresAt()).isEqualTo(expires);
        assertThat(second.issueDigests()).containsEntry(42, report.digest());
        now.set(expires.minusSeconds(1));
        String last = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, second);
        Instant receiptExpiry = now.get().plusSeconds(120);
        now.set(expires.plusSeconds(3));
        EFormRenderApproval unapproved = service.consume(request, user, 42, "123", PREVIEW, last);
        assertThat(unapproved).isNotNull();
        assertThat(unapproved.issueDigests()).isEmpty();
        assertThat(unapproved.permits(42, "999998", report)).isFalse();
        assertThat(unapproved.expiresAt()).isEqualTo(receiptExpiry);
        assertThat(service.consume(request, user, 42, "123", PREVIEW, last)).isNull();
    }

    @Test
    void ordinaryExpiredConsentDoesNotBecomeAnUnapprovedContinuation() {
        String token = service.issue(request, user, 42, "123", PREVIEW, report);
        now.set(now.get().plusSeconds(120));
        assertThat(service.consume(request, user, 42, "123", PREVIEW, token)).isNull();
    }

    @Test
    void receiptLifetimeIsBoundedFromItsOwnIssuanceAndCannotBeResurrected() {
        EFormRenderApproval consent = consume();
        now.set(consent.expiresAt().minusSeconds(1));
        String receipt = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consent);
        now.set(now.get().plusSeconds(120));
        assertThat(service.consume(request, user, 42, "123", PREVIEW, receipt)).isNull();
        assertThat(service.consume(request, user, 42, "123", PREVIEW, receipt)).isNull();
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consent)).isNull();
        assertThat(service.consume(request, user, 42, "123", PREVIEW, "missing-receipt")).isNull();
        assertThat(service.consume(request, user, 42, "123", PREVIEW, null)).isNull();
    }

    @Test
    void expiredConsentReceiptStillRejectsEveryForeignScopeAndConsumesInvalidAttempt() {
        for (String mismatch : java.util.List.of("session", "provider", "patient", "form", "operation")) {
            EFormRenderApproval consent = consume();
            now.set(consent.expiresAt().minusSeconds(1));
            String receipt = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consent);
            now.set(now.get().plusSeconds(4));
            assertThat(service.consume("session".equals(mismatch) ? request() : request,
                    "provider".equals(mismatch) ? user("999997") : user,
                    "form".equals(mismatch) ? 43 : 42,
                    "patient".equals(mismatch) ? "124" : "123",
                    "operation".equals(mismatch) ? EFormRenderApprovalService.Operation.EDOC : PREVIEW, receipt))
                    .as(mismatch).isNull();
            assertThat(service.consume(request, user, 42, "123", PREVIEW, receipt)).isNull();
        }
    }

    @Test
    void twoExpiredConsentReceiptConsumersProduceOnlyOneEmptyCapability() throws Exception {
        EFormRenderApproval consent = consume();
        now.set(consent.expiresAt().minusSeconds(1));
        String receipt = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consent);
        now.set(now.get().plusSeconds(4));
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        java.util.concurrent.Callable<EFormRenderApproval> consume = () -> {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("consumers not released");
            return service.consume(request, user, 42, "123", PREVIEW, receipt);
        };
        try {
            var first = workers.submit(consume);
            var second = workers.submit(consume);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var accepted = java.util.stream.Stream.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull).toList();
            assertThat(accepted).hasSize(1);
            assertThat(accepted.getFirst().issueDigests()).isEmpty();
            assertThat(accepted.getFirst().permits(42, "999998", report)).isFalse();
        } finally {
            go.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void replacementPreservesEveryApprovedDigestWithoutChangingRequestForm() {
        EFormRenderCompletenessReport attachedReport =
                new EFormRenderCompletenessReport(0, 2, 0, 0, false, false, false, false);
        EFormRenderApproval first = consume();
        String composite = service.issue(request, user, 42, "123", PREVIEW,
                attachedReport, first, 43);
        EFormRenderApproval consumed = service.consume(request, user, 42, "123", PREVIEW, composite);
        assertThat(service.reissueAfterCapacity(request, user, 43, "123", PREVIEW, consumed)).isNull();
        String replacement = service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consumed);
        EFormRenderApproval retry = service.consume(request, user, 42, "123", PREVIEW, replacement);
        assertThat(retry.permits(42, "999998", report)).isTrue();
        assertThat(retry.permits(43, "999998", attachedReport)).isTrue();
        assertThat(retry.permits(43, "999998", report)).isFalse();
        assertThat(retry.permits(44, "999998", attachedReport)).isFalse();
    }

    @Test
    void expiredConsumedApprovalCannotBeReissued() {
        EFormRenderApproval consumed = consume();
        now.set(consumed.expiresAt());
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consumed)).isNull();
    }

    @Test void rejectsDifferentSession() {
        assertThat(service.reissueAfterCapacity(request(), user, 42, "123", PREVIEW, consume())).isNull();
    }
    @Test void rejectsMissingSession() {
        assertThat(service.reissueAfterCapacity(new MockHttpServletRequest(), user, 42, "123", PREVIEW, consume())).isNull();
    }
    @Test void rejectsDifferentProvider() {
        assertThat(service.reissueAfterCapacity(request, user("999997"), 42, "123", PREVIEW, consume())).isNull();
    }
    @Test void rejectsDifferentRequestForm() {
        assertThat(service.reissueAfterCapacity(request, user, 43, "123", PREVIEW, consume())).isNull();
    }
    @Test void rejectsDifferentPatient() {
        assertThat(service.reissueAfterCapacity(request, user, 42, "456", PREVIEW, consume())).isNull();
    }
    @Test void rejectsDifferentOperation() {
        assertThat(service.reissueAfterCapacity(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, consume())).isNull();
    }
    @Test void rejectsStagedFaxAndUnconsumedCapabilities() {
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW,
                EFormRenderApproval.forStagedFaxPreview())).isNull();
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW,
                new EFormRenderApproval("999998", "123", PREVIEW,
                        java.util.Map.of(42, report.digest()), now.get().plusSeconds(120)))).isNull();
        assertThat(service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, null)).isNull();
    }

    @Test
    void simultaneousReissueCreatesOnlyOneUsableReplacement() throws Exception {
        EFormRenderApproval consumed = consume();
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        java.util.concurrent.Callable<String> attempt = () -> {
            ready.countDown();
            if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("retry contenders not released");
            return service.reissueAfterCapacity(request, user, 42, "123", PREVIEW, consumed);
        };
        try {
            var first = workers.submit(attempt);
            var second = workers.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var tokens = java.util.stream.Stream.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull).toList();
            assertThat(tokens).hasSize(1);
            assertThat(service.consume(request, user, 42, "123", PREVIEW, tokens.getFirst())).isNotNull();
        } finally {
            go.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private EFormRenderApproval consume() {
        return service.consume(request, user, 42, "123", PREVIEW,
                service.issue(request, user, 42, "123", PREVIEW, report));
    }

    @Test
    void emptyContinuationsUseOrdinaryTtlAndApproveNoOmissions() {
        String token = service.issueCapacityContinuation(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC);
        EFormRenderApproval continuation = service.consume(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC, token);
        assertThat(continuation.issueDigests()).isEmpty();
        assertThat(continuation.permits(42, "999998", report)).isFalse();
        assertThat(continuation.expiresAt()).isEqualTo(now.get().plusSeconds(120));
        assertThat(service.consume(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC, token)).isNull();
        String expires = service.issueCapacityContinuation(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC);
        now.set(now.get().plusSeconds(120));
        assertThat(service.consume(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC, expires)).isNull();
    }

    @Test
    void emptyContinuationsRemainBoundToSessionUserPatientFormAndOperation() {
        for (String mismatch : java.util.List.of("session", "provider", "patient", "form", "operation")) {
            String token = service.issueCapacityContinuation(request, user, 42, "123", EFormRenderApprovalService.Operation.EDOC);
            assertThat(service.consume("session".equals(mismatch) ? request() : request,
                    "provider".equals(mismatch) ? user("999997") : user,
                    "form".equals(mismatch) ? 43 : 42,
                    "patient".equals(mismatch) ? "124" : "123",
                    "operation".equals(mismatch) ? PREVIEW : EFormRenderApprovalService.Operation.EDOC, token))
                    .as(mismatch).isNull();
        }
    }
    private static MockHttpServletRequest request() {
        MockHttpServletRequest value = new MockHttpServletRequest(); value.getSession(); return value;
    }
    private static LoggedInInfo user(String provider) {
        LoggedInInfo value = mock(LoggedInInfo.class);
        when(value.getLoggedInProviderNo()).thenReturn(provider); return value;
    }
}
