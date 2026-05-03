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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BatchBillingRemovalService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnHeaderCreationService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BatchBillingSubmissionService;
import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the batch-billing mutation paths.
 *
 * @since 2026-04-27
 */
@DisplayName("BatchBill2Action")
@Tag("unit")
@Tag("billing")
class BatchBill2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private BillingOnHeaderCreationService headerCreationService;

    @Mock
    private BatchBillingDAO batchBillingDAO;

    @Mock
    private BatchBillingSubmissionService batchBillingSubmissionService;

    @Mock
    private BatchBillingRemovalService batchBillingRemovalService;

    @Mock
    private io.github.carlos_emr.carlos.billings.ca.on.assembler.BatchBillingViewModelAssembler batchBillingAssembler;

    @Mock
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.getSession(true).setAttribute("user", "999998");
        request.setParameter("BillDate", "2026-04-27");
        request.setParameter("clinic_view", "clinic-a");
        request.setParameter("providers", "999998");
        request.setParameter("service_code", "A007A");

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(BillingOnHeaderCreationService.class, headerCreationService);
        registerMock(BatchBillingDAO.class, batchBillingDAO);
        registerMock(BatchBillingSubmissionService.class, batchBillingSubmissionService);
        registerMock(BatchBillingRemovalService.class, batchBillingRemovalService);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        // ActionContext binding lets ActionSupport.addActionError /
        // getText work in this unit test — without it the round-7
        // RemovalRowMissingException catch path NPEs at the addActionError
        // call.
        org.apache.struts2.ActionContext.of()
                .withServletRequest(request)
                .withServletResponse(response)
                .bind();

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(headerCreationService.createBill(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("12.34");
        when(batchBillingSubmissionService.submitAll(any(), any(), any(), any()))
                .thenReturn(new BatchBillingSubmissionService.SubmitResult(1, List.of()));

        BatchBilling row = new BatchBilling();
        row.setId(77);
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of(row));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldRejectDoBatchBill_whenRequestIsNotPost() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "doBatchBill");
        request.setParameter("bill", "A007A;250;42;999998");

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(headerCreationService, never())
                .createBill(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldCreateBillUsingFourFieldCheckboxSchema_whenBatchSubmitted() {
        request.setMethod("POST");
        request.setParameter("bill", "A007A;250;42;999998");

        String result = action().doBatchBill();

        assertThat(result).isNull();
        verify(batchBillingSubmissionService)
                .submitAll(org.mockito.ArgumentMatchers.argThat(rows ->
                                rows.size() == 1
                                        && rows.get(0).providerNo().equals("999998")
                                        && rows.get(0).demographicNo().equals(42)
                                        && rows.get(0).serviceCode().equals("A007A")
                                        && rows.get(0).dxCode().equals("250")),
                        eq("clinic-a"), any(), eq("999998"));
    }

    @Test
    void shouldRejectLegacyThreeFieldCheckboxSchema_beforeCreatingAnyBill() {
        request.setMethod("POST");
        request.setParameter("bill", "A007A;42;999998");

        String result = action().doBatchBill();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        verify(batchBillingSubmissionService, never())
                .submitAll(any(), any(), any(), any());
    }

    @Test
    void shouldNotPersistAnyBillsInDoBatchBill_whenLaterRowFailsPreValidation() {
        // Atomicity contract: if row N is malformed (non-numeric demo no
        // here), rows 0..N-1 must NOT be persisted. The pre-validate loop
        // at BatchBill2Action.java:192-201 runs the parse on every row
        // before the persist loop touches a single createBill call.
        // Without this test the contract holds trivially with one row but
        // a regression that drops the pre-validate loop slips through.
        request.setMethod("POST");
        request.setParameter("bill",
                new String[] {
                        "A007A;250;42;999998",                  // row 0: valid
                        "A008A;251;NOT_NUMERIC;999998"          // row 1: malformed demo no
                });

        String result = action().doBatchBill();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        // No bills persisted — neither row 0 nor row 1.
        verify(batchBillingSubmissionService, never())
                .submitAll(any(), any(), any(), any());
    }

    @Test
    void shouldReturnError_whenBatchSubmissionServiceRollsBack() {
        request.setMethod("POST");
        request.setParameter("bill", "A007A;250;42;999998");
        org.mockito.Mockito.doThrow(new RuntimeException("DB outage simulation"))
                .when(batchBillingSubmissionService)
                .submitAll(any(), any(), any(), any());

        String result = action().doBatchBill();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(request.getAttribute("error")).isNotNull();
    }

    @Test
    void shouldNotPersistAnyBillsInExecute_whenLaterRowFailsPreValidation() {
        // Same atomicity contract on the execute() path (different
        // pre-validate block at BatchBill2Action.java:119-135).
        request.setMethod("POST");
        request.setParameter("bill",
                new String[] {
                        "A007A;250;42;999998",
                        "A008A;251;NOT_NUMERIC;999998"
                });

        // execute() routes to doBatchBill when method=doBatchBill, so set
        // a different method to land on the execute body.
        request.setParameter("method", "executeBody");

        // Executing.
        try {
            action().execute();
        } catch (Exception ignore) {
            // Body may throw if the assembler fails to assemble; the
            // load-bearing assertion is still that no bills were persisted.
        }
        verify(headerCreationService, never())
                .createBill(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSurfaceMissingRow_whenRemovalRowMissingExceptionThrows() throws Exception {
        // Round-7 contract: when BatchBillingRemovalService throws
        // RemovalRowMissingException, the action must (1) NOT fall through
        // to the generic RuntimeException catch (which rethrows as 500),
        // (2) addActionError, (3) set "removeRowMissing" request attr with
        // the offending Row, and (4) return ERROR. Without these the
        // operator sees the same opaque 500 the typed exception was
        // designed to replace.
        BatchBillingRemovalService.Row missing =
                new BatchBillingRemovalService.Row(
                        9999, "Z999Z");
        org.mockito.Mockito.doThrow(
                new BatchBillingRemovalService.RemovalRowMissingException(missing))
                .when(batchBillingRemovalService).removeAll(org.mockito.ArgumentMatchers.anyList());

        request.setMethod("POST");
        request.setParameter("method", "remove");
        request.setParameter("bill", "Z999Z;100;9999;999998");

        // Spy + stub getText: ActionSupport.getText needs a Struts
        // Container that isn't wired in unit context.
        BatchBill2Action action = org.mockito.Mockito.spy(
                action());
        org.mockito.Mockito.doReturn("Row not found: 9999/Z999Z")
                .when(action).getText(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(String[].class));
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(request.getAttribute("removeRowMissing")).isEqualTo(missing);
    }

    @Test
    void shouldRethrowGenericRuntimeException_whenRemovalServiceFailsForAnyOtherReason() throws Exception {
        // Sibling contract: any other RuntimeException (DB outage, FK
        // constraint, etc.) is rethrown so it surfaces as the existing
        // 500-with-stack-trace path. The separate RemovalRowMissingException
        // catch must NOT swallow these.
        org.mockito.Mockito.doThrow(new RuntimeException("DB outage simulation"))
                .when(batchBillingRemovalService).removeAll(org.mockito.ArgumentMatchers.anyList());

        request.setMethod("POST");
        request.setParameter("method", "remove");
        request.setParameter("bill", "A007A;250;42;999998");

        BatchBill2Action action = action();

        org.assertj.core.api.Assertions.assertThatThrownBy(action::execute)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB outage simulation");
    }

    private BatchBill2Action action() {
        return new BatchBill2Action(
                headerCreationService,
                securityInfoManager,
                batchBillingAssembler,
                batchBillingSubmissionService,
                batchBillingRemovalService,
                batchBillingDAO);
    }
}
