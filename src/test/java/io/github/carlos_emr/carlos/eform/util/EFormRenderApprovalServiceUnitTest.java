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
                new EFormRenderCompletenessReport(2, 1, 0, true, false, false, false);

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
                42, "999998", new EFormRenderCompletenessReport(3, 1, 0, true, false, false, false))).isFalse();
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
                new EFormRenderCompletenessReport(1, 0, 0, false, false, false, false);
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
                new EFormRenderCompletenessReport(1, 0, 0, false, false, false, false);
        EFormRenderCompletenessReport secondReport =
                new EFormRenderCompletenessReport(0, 2, 0, false, true, false, false);

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
