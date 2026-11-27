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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.integration.patientportal.payment.PortalPaymentRequestException;
import io.github.carlos_emr.carlos.integration.patientportal.payment.PortalPaymentRequestService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The staff payment-request action: POST only, both security objects, the amount is never read from
 * the request, and the invoice reference never leaves the server.
 */
@Tag("unit")
@Tag("patient-portal")
class PortalPaymentRequest2ActionUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final PortalPaymentRequestService requests = mock(PortalPaymentRequestService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo session = mock(LoggedInInfo.class);
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_PAYMENT_MANAGE));
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<LogAction> audit;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.setParameter("method", "create");
        request.setParameter("demographicNo", "123");
        request.setParameter("billingNo", "55");
        request.setParameter("category", "uninsured_service");
        request.setParameter("dueOn", LocalDate.now().plusDays(10).toString());
        audit = mockStatic(LogAction.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(session);
        when(session.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(staff);
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
        audit.close();
    }

    private JsonNode execute() throws Exception {
        new PortalPaymentRequest2Action(security, portal, resolver, requests).execute();
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        return new ObjectMapper().readTree(response.getContentAsString());
    }

    private PortalPaymentRequest row(String status) {
        PortalPaymentRequest row = new PortalPaymentRequest();
        row.setInvoiceRef("inv-secret-ref");
        row.setOperationId("op-secret");
        row.setBillingNo(55);
        row.setDemographicNo(123);
        row.setAmountCents(4500);
        row.setTaxCents(518);
        row.setCategory("uninsured_service");
        row.setDueOn(LocalDate.now().plusDays(10));
        row.setStatus(status);
        row.setCreatedBy("999998");
        row.setCreatedAt(new Date());
        return row;
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "DELETE", "post"})
    void shouldRejectUnsafeMethod_beforeAccessOrRemoteCalls(String method) throws Exception {
        request.setMethod(method);

        execute();

        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(security, resolver, requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_portal.payment_request", "_billing"})
    void shouldRefuseSending_whenAnyRequiredObjectIsDenied(String object) throws Exception {
        when(security.hasPrivilege(any(), eq(object), anyString(), eq("123"))).thenReturn(false);

        execute();

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(resolver, requests);
    }

    @Test
    void shouldRefuseTheListWithoutBillingRead_andAskForReadNotWrite() throws Exception {
        request.setParameter("method", "list");
        when(security.hasPrivilege(any(), eq("_billing"), eq("r"), eq("123"))).thenReturn(false);

        execute();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(security).hasPrivilege(any(), eq("_billing"), eq("r"), eq("123"));
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "pay", "delete", "LIST"})
    void shouldRefuseAnUnknownMethod(String method) throws Exception {
        request.setParameter("method", method);

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(requests);
    }

    @Test
    void shouldRefuseAMissingPatientOrInvoice() throws Exception {
        request.removeParameter("demographicNo");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRefuseAnInvoiceThatIsNotSelected() throws Exception {
        request.removeParameter("billingNo");

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"free text about a diagnosis", "", "OTHER"})
    void shouldRefuseACategoryOutsideTheClosedList(String category) throws Exception {
        request.setParameter("category", category);

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tomorrow", "2026-13-40", ""})
    void shouldRefuseADueDateThatIsNotADate(String dueOn) throws Exception {
        request.setParameter("dueOn", dueOn);

        execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(requests);
    }

    @Test
    void shouldSendTheInvoicesAmountNotOneFromTheRequest_andKeepTheReferenceOffTheWire() throws Exception {
        request.setParameter("amountCents", "1");
        request.setParameter("amount", "0.01");
        request.setParameter("taxCents", "0");
        when(requests.send(same(staff), eq("999998"), eq(123), eq(55), eq("uninsured_service"), any()))
                .thenReturn(new PortalPaymentRequestService.Sent(row("open"), true));

        JsonNode body = execute();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(body.get("created").asBoolean()).isTrue();
        assertThat(body.get("request").get("amountCents").asLong()).isEqualTo(4500);
        assertThat(body.toString()).doesNotContain("inv-secret-ref", "op-secret", "invoiceRef", "operationId");
        // Whatever the browser posted, the service is given only the invoice, patient and category.
        verify(requests).send(same(staff), eq("999998"), eq(123), eq(55), eq("uninsured_service"), any());
        audit.verify(() -> LogAction.addLog(any(LoggedInInfo.class), eq("PortalPaymentRequest2Action.create"),
                anyString(), anyString(), anyString(), anyString()), org.mockito.Mockito.atLeastOnce());
    }

    @Test
    void shouldAnswerOkNotCreated_whenTheSendIsARetry() throws Exception {
        when(requests.send(any(), anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(new PortalPaymentRequestService.Sent(row("open"), false));

        JsonNode body = execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("created").asBoolean()).isFalse();
    }

    @Test
    void shouldTellStaffWhenThePatientHasNoPortalAccount() throws Exception {
        when(requests.send(any(), anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenThrow(PatientPortalException.ofStatus(404, "/internal/carlos/patients/%d/payment-requests",
                        PatientPortalException.ACCOUNT_NOT_FOUND_DETAIL));

        JsonNode body = execute();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(body.get("reason").asText()).isEqualTo("portal_account_inactive");
    }

    @Test
    void shouldAnswer404ForAnInvoiceThatIsNotThePatients_and409ForAnOpenDifferentRequest() throws Exception {
        when(requests.send(any(), anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenThrow(new PortalPaymentRequestException(PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND));
        JsonNode missing = execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(missing.get("reason").asText()).isEqualTo("invoice_not_found");

        response.reset();
        when(requests.send(any(), anyString(), anyInt(), anyInt(), anyString(), any()))
                .thenThrow(new PortalPaymentRequestException(PortalPaymentRequestException.Reason.OPEN_REQUEST_DIFFERS));
        JsonNode differs = execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(differs.get("reason").asText()).isEqualTo("open_request_differs");
    }

    @Test
    void shouldListOnlyTheSelectedPatientsRequests() throws Exception {
        request.setParameter("method", "list");
        PortalPaymentRequest other = row("open");
        other.setDemographicNo(999);
        when(requests.listForInvoice(same(staff), eq(123), eq(55))).thenReturn(List.of(row("paid"), other));

        JsonNode body = execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("requests")).hasSize(1);
        assertThat(body.get("requests").get(0).get("status").asText()).isEqualTo("paid");
    }

    @Test
    void shouldWithdrawByRequestId_andRefuseAMissingId() throws Exception {
        request.setParameter("method", "withdraw");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(requests);

        response.reset();
        request.setParameter("requestId", "9");
        when(requests.withdraw(same(staff), eq(123), eq(55), eq(9L))).thenReturn(row("withdrawn"));
        JsonNode body = execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body.get("request").get("status").asText()).isEqualTo("withdrawn");
        verify(requests).withdraw(same(staff), eq(123), eq(55), eq(9L));
    }

    @Test
    void shouldAnswer409_whenWithdrawingAPaidRequest() throws Exception {
        request.setParameter("method", "withdraw");
        request.setParameter("requestId", "9");
        when(requests.withdraw(any(), anyInt(), anyInt(), anyLong()))
                .thenThrow(new PortalPaymentRequestException(PortalPaymentRequestException.Reason.REQUEST_PAID));

        JsonNode body = execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(body.get("reason").asText()).isEqualTo("request_paid");
    }
}
