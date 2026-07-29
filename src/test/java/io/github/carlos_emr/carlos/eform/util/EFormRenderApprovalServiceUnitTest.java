/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormRenderApprovalService")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormRenderApprovalServiceUnitTest {

    @Test
    @DisplayName("should issue a one-time approval bound to session, user, patient, form, and operation")
    void shouldIssueOneTimeApproval_withExactRequestBindings() {
        EFormRenderApprovalService service = new EFormRenderApprovalService();
        MockHttpServletRequest request = requestWithSession();
        LoggedInInfo user = user("999998");
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 1, 0, 0, true, false, false, false);

        String token = service.issue(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, report);
        EFormRenderApproval approval = service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, token);

        assertThat(token).matches("[A-Za-z0-9_-]{40,}");
        assertThat(approval).isNotNull().hasToString("[eform-render-approval]");
        assertThat(approval.permits(42, "999998", report)).isTrue();
        assertThat(approval.permits(43, "999998", report)).isFalse();
        assertThat(approval.permits(42, "999997", report)).isFalse();
        assertThat(approval.permits(
                42, "999998", new EFormRenderCompletenessReport(3, 1, 0, 0, true, false, false, false))).isFalse();
        assertThat(service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, token)).isNull();
    }

    @Test
    @DisplayName("should consume and reject a ticket presented for a different operation")
    void shouldRejectAndConsumeTicket_forDifferentOperation() {
        EFormRenderApprovalService service = new EFormRenderApprovalService();
        MockHttpServletRequest request = requestWithSession();
        LoggedInInfo user = user("999998");
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false);
        String token = service.issue(request, user, 42, "123",
                EFormRenderApprovalService.Operation.PREVIEW, report);

        assertThat(service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, token)).isNull();
        assertThat(service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.PREVIEW, token)).isNull();
    }

    @Test
    @DisplayName("should accumulate exact approvals for multiple eForms in one composite document")
    void shouldAccumulateExactApprovals_forCompositeDocument() {
        EFormRenderApprovalService service = new EFormRenderApprovalService();
        MockHttpServletRequest request = requestWithSession();
        LoggedInInfo user = user("999998");
        EFormRenderCompletenessReport firstReport =
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false);
        EFormRenderCompletenessReport secondReport =
                new EFormRenderCompletenessReport(0, 2, 0, 0, false, true, false, false);

        String firstToken = service.issue(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, firstReport, null, 101);
        EFormRenderApproval firstApproval = service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, firstToken);
        String secondToken = service.issue(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, secondReport, firstApproval, 102);
        EFormRenderApproval combined = service.consume(request, user, 42, "123",
                EFormRenderApprovalService.Operation.FAX, secondToken);

        assertThat(combined).isNotNull();
        assertThat(combined.permits(101, "999998", firstReport)).isTrue();
        assertThat(combined.permits(102, "999998", secondReport)).isTrue();
        assertThat(combined.permits(42, "999998", firstReport)).isFalse();
    }

    @Test
    @DisplayName("should refuse to issue an approval for a complete render")
    void shouldRefuseApproval_forCompleteRender() {
        EFormRenderApprovalService service = new EFormRenderApprovalService();

        assertThatThrownBy(() -> service.issue(
                requestWithSession(), user("999998"), 42, "123",
                EFormRenderApprovalService.Operation.PREVIEW,
                EFormRenderCompletenessReport.complete()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The six checks {@code consume} makes, one test each.
     *
     * <p>They share a single compound condition and a single {@code return null}, so before these
     * existed only two of the six were covered — replay and wrong operation. The demographic check
     * is the one that stops a ticket minted for one patient being spent on another's document, and
     * it had no test at all.</p>
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("consume rejects a ticket that does not match")
    class ConsumeBindings {

        private final EFormRenderApprovalService service = new EFormRenderApprovalService();
        private final MockHttpServletRequest request = requestWithSession();
        private final LoggedInInfo user = user("999998");
        private String token;

        @org.junit.jupiter.api.BeforeEach
        void issueTicket() {
            token = service.issue(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, incompleteReport());
        }

        @Test
        @DisplayName("should reject a ticket presented for a different patient")
        void shouldReject_forDifferentDemographic() {
            // Cross-patient reuse: the highest-risk single line in the service.
            assertThat(service.consume(request, user, 42, "456",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token)).isNull();
        }

        @Test
        @DisplayName("should reject a ticket presented from a different session")
        void shouldReject_fromDifferentSession() {
            MockHttpServletRequest otherBrowser = requestWithSession();

            assertThat(service.consume(otherBrowser, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token)).isNull();
        }

        @Test
        @DisplayName("should reject a ticket presented by a different provider")
        void shouldReject_forDifferentProvider() {
            assertThat(service.consume(request, user("999997"), 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token)).isNull();
        }

        @Test
        @DisplayName("should reject a ticket presented for a different eForm")
        void shouldReject_forDifferentFdid() {
            assertThat(service.consume(request, user, 43, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token)).isNull();
        }

        @Test
        @DisplayName("should reject a ticket presented for a different operation")
        void shouldReject_forDifferentOperation() {
            assertThat(service.consume(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.FAX, token)).isNull();
        }

        @Test
        @DisplayName("should reject a ticket after its two-minute lifetime")
        void shouldReject_afterTicketLifetime() {
            // The approval page is a list of clinical omissions meant to be read, so exceeding the
            // TTL is an ordinary outcome rather than an attack.
            //
            // This asserts the CONTRACT (an expired ticket is refused), not one mechanism. Expiry is
            // enforced twice — Caffeine's expireAfterWrite eviction and the explicit isAfter(...)
            // comparison in consume — and both read the injected clock, so the cache drops the entry
            // first and consume sees a null pending. Verified by mutation: deleting the explicit
            // comparison leaves this green. It is kept as defence in depth, because the two would
            // diverge the moment the cache's eviction policy or ticker changed, and the failure mode
            // of losing it silently is an approval that outlives its stated lifetime.
            java.time.Instant start = java.time.Instant.parse("2026-07-27T10:00:00Z");
            java.util.concurrent.atomic.AtomicReference<java.time.Instant> now =
                    new java.util.concurrent.atomic.AtomicReference<>(start);
            EFormRenderApprovalService clocked =
                    new EFormRenderApprovalService(movableClock(now));
            String issued = clocked.issue(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, incompleteReport());

            now.set(start.plusSeconds(119));
            assertThat(clocked.consume(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, issued))
                    .describedAs("still inside the two-minute window").isNotNull();

            String second = clocked.issue(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, incompleteReport());
            now.set(now.get().plusSeconds(121));
            assertThat(clocked.consume(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, second)).isNull();
        }

        @Test
        @DisplayName("should refuse to inherit digests from another provider's approval")
        void shouldRefuseInheritingDigests_fromAnotherProvider() {
            EFormRenderApproval mine = service.consume(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token);

            assertThatThrownBy(() -> service.issue(request, user("999997"), 43, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, incompleteReport(), mine, 43))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("different provider");
        }

        @Test
        @DisplayName("should refuse to inherit digests approved for another patient or operation")
        void shouldRefuseInheritingDigests_fromAnotherScope() {
            // A composite flow that previewed a document and then reused that approval to seed a fax
            // ticket would otherwise promote PREVIEW-approved omissions into a FAX: the clinician
            // consented to look at an incomplete document, not to send one.
            EFormRenderApproval forDownload = service.consume(request, user, 42, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD, token);

            assertThatThrownBy(() -> service.issue(request, user, 43, "123",
                    EFormRenderApprovalService.Operation.FAX, incompleteReport(), forDownload, 43))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("different patient or operation");
            assertThatThrownBy(() -> service.issue(request, user, 43, "456",
                    EFormRenderApprovalService.Operation.DOWNLOAD, incompleteReport(), forDownload, 43))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("different patient or operation");
        }
    }


    @Test
    @DisplayName("should keep a staged fax preview until it is claimed or its session is destroyed")
    void shouldKeepStagedFaxPreview_untilClaimOrSessionDestruction() throws java.io.IOException {
        java.time.Instant start = java.time.Instant.parse("2026-07-28T10:00:00Z");
        java.util.concurrent.atomic.AtomicReference<java.time.Instant> now =
                new java.util.concurrent.atomic.AtomicReference<>(start);
        EFormRenderApprovalService service = new EFormRenderApprovalService(movableClock(now));
        MockHttpServletRequest request = requestWithSession();
        LoggedInInfo user = user("999998");
        java.nio.file.Path root = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "carlos-temp");
        java.nio.file.Files.createDirectories(root);
        java.nio.file.Path testRoot = java.nio.file.Files.createTempDirectory(root, "staged-fax-test-");
        java.nio.file.Path claimed = java.nio.file.Files.createTempFile(testRoot, "claimed-", ".pdf");
        java.nio.file.Path abandoned = java.nio.file.Files.createTempFile(testRoot, "abandoned-", ".pdf");
        try {
            String claimToken = service.issueStagedFaxPreview(request, user, 42, "123",
                    java.util.Map.of(42, incompleteReport()), 1, claimed);
            now.set(start.plus(java.time.Duration.ofHours(3)));
            EFormRenderApprovalService.StagedFaxPreview staged =
                    service.consumeStagedFaxPreview(request, user, 42, "123", claimToken);
            assertThat(staged)
                    .describedAs("a staged fax preview has no arbitrary wall-clock expiry")
                    .isNotNull();
            assertThat(staged.advisoryIssueCount()).isEqualTo(1);
            assertThat(java.nio.file.Files.exists(claimed))
                    .describedAs("the claimed PDF remains available for the fax pipeline")
                    .isTrue();

            String abandonedToken = service.issueStagedFaxPreview(request, user, 42, "123",
                    java.util.Map.of(42, incompleteReport()), 0, abandoned);
            service.invalidateStagedFaxPreviewsForSession(request.getSession().getId());

            assertThat(java.nio.file.Files.exists(claimed)).isTrue();
            assertThat(java.nio.file.Files.exists(abandoned)).isFalse();
            assertThat(service.consumeStagedFaxPreview(request, user, 42, "123", abandonedToken)).isNull();
        } finally {
            java.nio.file.Files.deleteIfExists(claimed);
            java.nio.file.Files.deleteIfExists(abandoned);
            java.nio.file.Files.deleteIfExists(testRoot);
        }
    }

    private static EFormRenderCompletenessReport incompleteReport() {
        return new EFormRenderCompletenessReport(2, 1, 0, 0, true, false, false, false);
    }

    /** A clock whose instant the test moves, so the TTL can be crossed without waiting. */
    private static java.time.Clock movableClock(
            java.util.concurrent.atomic.AtomicReference<java.time.Instant> now) {
        return new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public java.time.Instant instant() {
                return now.get();
            }
        };
    }

    private static MockHttpServletRequest requestWithSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        return request;
    }

    private static LoggedInInfo user(String providerNo) {
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn(providerNo);
        return user;
    }
}
