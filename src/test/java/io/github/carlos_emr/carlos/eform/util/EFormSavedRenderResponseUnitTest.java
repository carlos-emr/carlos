/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Map;

import static io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService.Operation.DOWNLOAD;
import static io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService.Operation.EDOC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
class EFormSavedRenderResponseUnitTest {
    private final EFormRenderApprovalService service = new EFormRenderApprovalService();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final EFormRenderCompletenessReport report = new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false);

    EFormSavedRenderResponseUnitTest() {
        request.getSession();
        request.setContextPath("/carlos");
        request.setParameter("clinicalNote", "must not travel to continuation");
        when(user.getLoggedInProviderNo()).thenReturn("999998");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fields() {
        return (Map<String, String>) request.getAttribute("renderCapacityFields");
    }

    @Test
    void initialArchiveCapacityCreatesOneUseContinuationApprovingNoOmissions() {
        assertThat(EFormSavedRenderResponse.busy(request, response, service, user, 42, "123", EDOC, null, true))
                .isEqualTo("renderBusy");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getAttribute("renderCapacityAction")).isEqualTo("/carlos/eform/saveEFormAsEDoc");
        assertThat(fields()).containsOnlyKeys("fdid", "demographicNo", "renderApproval", "autoClose");
        EFormRenderApproval continuation = service.consume(request, user, 42, "123", EDOC, fields().get("renderApproval"));
        assertThat(continuation).isNotNull();
        assertThat(continuation.issueDigests()).isEmpty();
        assertThat(continuation.permits(42, "999998", report)).isFalse();
        assertThat(service.consume(request, user, 42, "123", EDOC, fields().get("renderApproval"))).isNull();
    }

    @Test
    void busyApprovedDownloadRotatesExactConsentWithoutExtendingExpiry() {
        String initial = service.issue(request, user, 42, "123", DOWNLOAD, report);
        EFormRenderApproval previous = service.consume(request, user, 42, "123", DOWNLOAD, initial);
        EFormSavedRenderResponse.busy(request, response, service, user, 42, "123", DOWNLOAD, previous, true);
        assertThat(fields().get("renderApproval")).isNotEqualTo(initial);
        EFormRenderApproval next = service.consume(request, user, 42, "123", DOWNLOAD, fields().get("renderApproval"));
        assertThat(next.issueDigests()).isEqualTo(previous.issueDigests());
        assertThat(next.expiresAt()).isEqualTo(previous.expiresAt());
        assertThat(fields().get("autoClose")).isEqualTo("true");
    }

    @Test
    void expiredConsentAfterBusyBecomesEmptyContinuationInsteadOfRenewedOmissions() {
        EFormRenderApproval expired = new EFormRenderApproval("999998", "123", EDOC,
                Map.of(42, report.digest()), Instant.EPOCH, request.getSession().getId(), 42);
        EFormSavedRenderResponse.busy(request, response, service, user, 42, "123", EDOC, expired, false);
        EFormRenderApproval next = service.consume(request, user, 42, "123", EDOC, fields().get("renderApproval"));
        assertThat(next.issueDigests()).isEmpty();
        assertThat(next.permits(42, "999998", report)).isFalse();
        assertThat(fields()).doesNotContainKey("autoClose");
    }

    @Test
    void missingAttachmentPromptsAndPreservesPriorExactDigestsAndDownloadIntent() {
        EFormRenderApproval previous = service.consume(request, user, 42, "123", DOWNLOAD,
                service.issue(request, user, 42, "123", DOWNLOAD, report));
        EFormRenderCompletenessReport attachment = new EFormRenderCompletenessReport(0, 2, 0, 0, false, false, false, false);
        EformContentUnavailableException failure = new EformContentUnavailableException("incomplete", 43, attachment);
        assertThat(EFormSavedRenderResponse.missing(request, service, user, failure, 42, "123", DOWNLOAD, previous, true))
                .isEqualTo("missingContent");
        assertThat(request.getAttribute("approvalAction")).isEqualTo("eform/downloadEFormPdf");
        assertThat(request.getAttribute("approvalAutoClose")).isEqualTo("true");
        assertThat(request.getAttribute("excludedContentElements")).isEqualTo(2);
        EFormRenderApproval combined = service.consume(request, user, 42, "123", DOWNLOAD,
                (String) request.getAttribute("renderApproval"));
        assertThat(combined.permits(42, "999998", report)).isTrue();
        assertThat(combined.permits(43, "999998", attachment)).isTrue();
        assertThat(combined.permits(43, "999998", report)).isFalse();
    }

    @Test
    void expiredOtherOmissionsAreDiscardedWhenPromptingForAnAttachment() {
        EFormRenderApproval expired = new EFormRenderApproval("999998", "123", EDOC,
                Map.of(42, report.digest()), Instant.EPOCH);
        var changed = new EFormRenderCompletenessReport(3, 0, 0, 0, false, false, false, false);
        EFormSavedRenderResponse.missing(request, service, user,
                new EformContentUnavailableException("incomplete", 43, changed), 42, "123", EDOC, expired, true);
        EFormRenderApproval refreshed = service.consume(request, user, 42, "123", EDOC,
                (String) request.getAttribute("renderApproval"));
        assertThat(refreshed.issueDigests()).containsOnlyKeys(43);
        assertThat(refreshed.permits(42, "999998", report)).isFalse();
        assertThat(refreshed.permits(43, "999998", changed)).isTrue();
        assertThat(request.getAttribute("approvalAction")).isEqualTo("eform/saveEFormAsEDoc");
    }

    @Test
    void changedIssuesOnTheSameFormRequireTheNewExactDigest() {
        EFormRenderApproval previous = service.consume(request, user, 42, "123", DOWNLOAD,
                service.issue(request, user, 42, "123", DOWNLOAD, report));
        var changed = new EFormRenderCompletenessReport(3, 0, 0, 0, false, false, false, false);
        EFormSavedRenderResponse.missing(request, service, user,
                new EformContentUnavailableException("changed", 42, changed), 42, "123", DOWNLOAD, previous, false);
        EFormRenderApproval refreshed = service.consume(request, user, 42, "123", DOWNLOAD,
                (String) request.getAttribute("renderApproval"));
        assertThat(refreshed.permits(42, "999998", report)).isFalse();
        assertThat(refreshed.permits(42, "999998", changed)).isTrue();
    }
}
