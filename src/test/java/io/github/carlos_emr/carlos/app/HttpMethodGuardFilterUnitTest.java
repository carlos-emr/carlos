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
package io.github.carlos_emr.carlos.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpMethodGuardFilter}.
 *
 * <p>Validates that GET requests to mutator endpoints are blocked with 405,
 * while POST requests and GET requests to read-only endpoints pass through.</p>
 *
 * @since 2026-03-13
 */
@Tag("unit")
@Tag("security")
@DisplayName("HttpMethodGuardFilter")
class HttpMethodGuardFilterUnitTest {

    private HttpMethodGuardFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        filter = new HttpMethodGuardFilter();
        FilterConfig filterConfig = mock(FilterConfig.class);
        when(filterConfig.getInitParameter("allowList")).thenReturn(null);
        filter.init(filterConfig);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Nested
    @DisplayName("POST requests")
    class PostRequests {

        @Test
        @DisplayName("should pass through POST to mutator action")
        void shouldPassThrough_forPostToMutatorAction() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/addTickler");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through POST to mutator JSP")
        void shouldPassThrough_forPostToMutatorJsp() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/carlos/admin/securitydelete.jsp");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("GET requests to read-only actions")
    class ReadOnlyActions {

        @Test
        @DisplayName("should pass through GET to display action")
        void shouldPassThrough_forGetToDisplayAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/web/dashboard/display/DashboardDisplay");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to view method parameter")
        void shouldPassThrough_forGetWithViewMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SystemMessage");
            when(request.getParameter("method")).thenReturn("view");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to edit action (edit loads forms via GET)")
        void shouldPassThrough_forGetToEditAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/EditTickler");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to CreateDate (dual-purpose schedule action)")
        void shouldPassThrough_forGetToCreateDateAction() throws Exception {
            // CreateDate starts with "create" (a mutator prefix) but serves month-
            // navigation reloads on GET (bFirstDisp=0). ScheduleCreateDate2Action
            // enforces POST internally for real mutations (bFirstDisp=null|"1").
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/schedule/CreateDate");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to report JSP")
        void shouldPassThrough_forGetToReportJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/logReport.jsp");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to search JSP")
        void shouldPassThrough_forGetToSearchJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/demographic/demographicCohort.jsp");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to moveMOHFiles render action")
        void shouldPassThrough_forGetToMoveMohFilesRenderAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/CA/ON/moveMOHFiles");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to the BC reprocessBill route, which opens Bill Status and the adjust bill page")
        void shouldPassThrough_forGetToBcReprocessBillOpenAction() throws Exception {
            // BillingReProcessBill2Action itself answers 405 to a GET carrying billCheck or billingmasterNo (#4343).
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/CA/BC/reprocessBill");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to AddRelation popup render action")
        void shouldPassThrough_forGetToAddRelationAction() throws Exception {
            // AddRelation starts with "add" (a mutator prefix) but the "Add Relation" popup
            // (edit-view.jsp) opens it via GET with only `demo` to render the contact-search
            // form. AddDemographicRelationship2Action only persists when linkingDemo+relation
            // are present, and rejects that case unless the request is POST.
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/demographic/AddRelation");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to the RH form's injection editor view")
        void shouldPassThrough_forGetToRhInjectionEditor() throws Exception {
            // form/addRhInjection starts with "add" but is a ViewForm2Action wildcard view: the RH
            // form's Add Injection opens it with GET, and the injection is stored by the editor's
            // POST to prevention/AddPrevention (#4992).
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/form/addRhInjection");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("GET requests to mutator actions")
    class MutatorActions {

        @Test
        @DisplayName("should block GET to Add* action")
        void shouldBlock_forGetToAddAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/addTickler");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "GET requests are not allowed on this endpoint. Use POST.");
            verify(response).setHeader("Allow", "POST");
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Delete* action")
        void shouldBlock_forGetToDeleteAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/oscarRx/deleteRx");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Save* action")
        void shouldBlock_forGetToSaveAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SaveAssoc");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET when path parameter appears before mutator action")
        void shouldBlock_whenPathParameterAppearsBeforeMutatorAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing;v=1/CA/ON/SaveBilling");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Submit* action")
        void shouldBlock_forGetToSubmitAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/prevention/SubmitImmunization");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Create* action")
        void shouldBlock_forGetToCreateAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/CreateBillingReport");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Remove* action")
        void shouldBlock_forGetToRemoveAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/RemoveFromGroup");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Assign* action")
        void shouldBlock_forGetToAssignAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/web/dashboard/display/AssignTickler");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to DocumentErrorReportUpload")
        void shouldBlock_forGetToDocumentErrorReportUpload() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/oscarBilling/DocumentErrorReportUpload");
            when(request.getParameter("filename")).thenReturn("R1234567");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "GET requests are not allowed on this endpoint. Use POST.");
            verify(response).setHeader("Allow", "POST");
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("GET requests with mutator method parameter")
    class MutatorMethodParams {

        @Test
        @DisplayName("should block GET with method=save on mixed action")
        void shouldBlock_forGetWithSaveMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SystemMessage");
            when(request.getParameter("method")).thenReturn("save");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET with method=delete")
        void shouldBlock_forGetWithDeleteMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SomeAction");
            when(request.getParameter("method")).thenReturn("delete");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET with method=update")
        void shouldBlock_forGetWithUpdateMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SomeAction");
            when(request.getParameter("method")).thenReturn("update");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through GET with method=list (read operation)")
        void shouldPassThrough_forGetWithListMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SystemMessage");
            when(request.getParameter("method")).thenReturn("list");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET with method=edit (loads form)")
        void shouldPassThrough_forGetWithEditMethodParam() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SystemMessage");
            when(request.getParameter("method")).thenReturn("edit");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    /**
     * Mixed-method actions name most of their writes with a compound value (the provider preference
     * saves, the program and staff editors, the HRM report changes): each counts as the verb it starts
     * with. Every page that sends one of these sends it by POST, so only GET and HEAD are refused.
     */
    @Nested
    @DisplayName("GET requests with a compound mutator method parameter")
    class CompoundMutatorMethodParams {

        @ParameterizedTest
        @ValueSource(strings = {"saveRxPageSize", "deleteComment", "removeTarget", "updateCategory",
                "assign_role", "cancelStagedEFormFax", "SaveRxPageSize"})
        @DisplayName("should block GET with a method value that starts with a mutator verb")
        void shouldBlock_forGetWithCompoundMutatorMethod(String method) throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/setProviderStaleDate");
            when(request.getParameter("method")).thenReturn(method);

            filter.doFilter(request, response, chain);

            verify(response).setHeader("Allow", "POST");
            verify(response).sendError(eq(405), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block HEAD with a compound mutator method")
        void shouldBlock_forHeadWithCompoundMutatorMethod() throws Exception {
            when(request.getMethod()).thenReturn("HEAD");
            when(request.getRequestURI()).thenReturn("/carlos/hospitalReportManager/Modify");
            when(request.getParameter("method")).thenReturn("updateCategory");

            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(405), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @ParameterizedTest
        @ValueSource(strings = {"viewRxPageSize", "editRemove", "ticklerGetNote", "list", "fetch"})
        @DisplayName("should pass through GET with a method value that does not start with a mutator verb")
        void shouldPassThrough_forGetWithReadMethod(String method) throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/setProviderStaleDate");
            when(request.getParameter("method")).thenReturn(method);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through POST with a compound mutator method")
        void shouldPassThrough_forPostWithCompoundMutatorMethod() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/carlos/setProviderStaleDate");
            when(request.getParameter("method")).thenReturn("saveRxPageSize");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should label the refusal of a compound method name in its one log line")
        void shouldLabelRefusal_forCompoundMutatorMethod() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/setProviderStaleDate");
            when(request.getParameter("method")).thenReturn("saveRxPageSize");

            try (LogCapture logs = LogCapture.forLogger(HttpMethodGuardFilter.class)) {
                filter.doFilter(request, response, chain);

                assertThat(logs.messages()).hasSize(1);
                assertThat(logs.messages().get(0))
                        .contains("/setProviderStaleDate?method=saveRxPageSize")
                        .contains("(compound method name)");
            }
        }

        @Test
        @DisplayName("should not label the refusal of an exact method name")
        void shouldNotLabelRefusal_forExactMutatorMethod() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/SystemMessage");
            when(request.getParameter("method")).thenReturn("save");

            try (LogCapture logs = LogCapture.forLogger(HttpMethodGuardFilter.class)) {
                filter.doFilter(request, response, chain);

                assertThat(logs.messages()).hasSize(1);
                assertThat(logs.messages().get(0)).doesNotContain("compound");
            }
        }
    }

    /**
     * Satellite-sites Admin (issue #4700): the site list opens a site with a GET link, so that link uses the
     * read-only method=edit; method=update and method=save stay refused on GET.
     */
    @Nested
    @DisplayName("Satellite-sites Admin (admin/ManageSites)")
    class ManageSites {

        @Test
        @DisplayName("should pass through the list's GET link with method=edit")
        void shouldPassThrough_forGetWithEditMethodOnManageSites() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/ManageSites");
            when(request.getParameter("method")).thenReturn("edit");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"update", "save"})
        @DisplayName("should block GET with method=update or method=save")
        void shouldBlock_forGetWithWriteMethodOnManageSites(String method) throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/ManageSites");
            when(request.getParameter("method")).thenReturn(method);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("GET requests to mutator JSPs")
    class MutatorJsps {

        @Test
        @DisplayName("should block GET to securitydelete.jsp")
        void shouldBlock_forGetToSecurityDeleteJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/securitydelete.jsp");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to providerupdate.jsp")
        void shouldBlock_forGetToProviderUpdateJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/providerupdate.jsp");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to billingONSave.jsp")
        void shouldBlock_forGetToBillingSaveJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/CA/ON/billingONSave.jsp");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        // Note: the former appointmentcontrol dispatcher route has been removed.
        // Edit/add appointment flows now target their final action endpoints directly.
        // (ViewAppointment2Action gate). The public JSP path returns 404 at the
        // servlet layer before this filter ever sees it, so the prior test was
        // exercising a dead URL. End-to-end coverage of the gated action path lives in
        // ViewAppointment2ActionTest.

        @Test
        @DisplayName("should block GET to PreventionManager.jsp with formAction=update")
        void shouldBlock_forGetToPreventionManagerWithUpdate() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/prevention/PreventionManager.jsp");
            when(request.getParameter("formAction")).thenReturn("update");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(Collections.singletonList("formAction")));

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through GET to PreventionManager.jsp without formAction")
        void shouldPassThrough_forGetToPreventionManagerWithoutFormAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/prevention/PreventionManager.jsp");
            when(request.getParameter("formAction")).thenReturn(null);
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(Collections.emptyList()));

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should block GET to lotnrdeleterecord.jsp (explicit mutator JSP)")
        void shouldBlock_forGetToLotnrDeleteRecordJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/lotnrdeleterecord.jsp");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Add2WaitingList.jsp (unconditional mutator)")
        void shouldBlock_forGetToAdd2WaitingListJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/waitinglist/Add2WaitingList.jsp");

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to admin JSP with submit=save")
        void shouldBlock_forGetToAdminJspWithSave() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/demographicmergerecord.jsp");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(Collections.singletonList("submit")));

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through GET to admin JSP without mutator params")
        void shouldPassThrough_forGetToAdminJspWithoutSave() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/demographicmergerecord.jsp");
            when(request.getParameterNames()).thenReturn(
                    Collections.enumeration(Collections.emptyList()));

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("GET requests to actions with mismatched URL/class names")
    class MismatchedActionNames {

        @Test
        @DisplayName("should block GET to billingAddCode (BillingAddCode2Action)")
        void shouldBlock_forGetToBillingAddCode() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/CA/BC/billingAddCode");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to rbtAddToGroup (RBTAddToGroup2Action)")
        void shouldBlock_forGetToRbtAddToGroup() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/oscarReport/reportByTemplate/actions/rbtAddToGroup");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to ReportReassign (ReportReassign2Action)")
        void shouldBlock_forGetToReportReassign() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/oscarMDS/ReportReassign");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to Forward (ReportReassign2Action lab forwarding)")
        void shouldBlock_forGetToForward() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/lab/CA/ALL/Forward");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("GET requests with abbreviated prefixes (del/rem)")
    class AbbreviatedPrefixes {

        @Test
        @DisplayName("should block GET to delGroup (DeleteGroup2Action)")
        void shouldBlock_forGetToDelGroup() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/eforms/delGroup");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to DelService (EctConDeleteServices2Action)")
        void shouldBlock_forGetToDelService() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/encounter/DelService");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should block GET to remFromGroup (RBTRemoveFromGroup2Action)")
        void shouldBlock_forGetToRemFromGroup() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/oscarReport/reportByTemplate/actions/remFromGroup");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("HEAD requests (blocked same as GET)")
    class HeadRequests {

        @Test
        @DisplayName("should block HEAD to mutator action")
        void shouldBlock_forHeadToMutatorAction() throws Exception {
            when(request.getMethod()).thenReturn("HEAD");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/addTickler");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }

        @Test
        @DisplayName("should pass through HEAD to read-only action")
        void shouldPassThrough_forHeadToReadOnlyAction() throws Exception {
            when(request.getMethod()).thenReturn("HEAD");
            when(request.getRequestURI()).thenReturn("/carlos/web/dashboard/display/DashboardDisplay");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("Confirmation pages (past-tense names should NOT be blocked)")
    class ConfirmationPages {

        @Test
        @DisplayName("should pass through GET to batchsaved.jsp (confirmation page)")
        void shouldPassThrough_forGetToBatchSavedJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/admin/batchsaved.jsp");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to billingcreated.jsp (confirmation page)")
        void shouldPassThrough_forGetToBillingCreatedJsp() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/billing/billingcreated.jsp");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to efmformmanagerdeleted (confirmation page)")
        void shouldPassThrough_forGetToDeletedConfirmationAction() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/eform/efmformmanagerdeleted");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("Allow-list overrides")
    class AllowList {

        @Test
        @DisplayName("should pass through GET to allow-listed mutator action")
        void shouldPassThrough_forAllowListedAction() throws Exception {
            // Re-initialize with an allow-list
            FilterConfig filterConfig = mock(FilterConfig.class);
            when(filterConfig.getInitParameter("allowList")).thenReturn("addticklerview");
            filter.init(filterConfig);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/AddTicklerView");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through GET to allow-listed mutator JSP")
        void shouldPassThrough_forAllowListedJsp() throws Exception {
            FilterConfig filterConfig = mock(FilterConfig.class);
            when(filterConfig.getInitParameter("allowList")).thenReturn("editDocument.jsp");
            filter.init(filterConfig);

            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/documentManager/editDocument.jsp");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("Prevention form view gate")
    class PreventionFormViewGate {

        @Test
        @DisplayName("should pass through GET to prevention/ViewAddPreventionData")
        void shouldPassThrough_forGetToViewAddPreventionData() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/prevention/ViewAddPreventionData");
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
            verify(response, never()).sendError(anyInt());
        }

        @Test
        @DisplayName("should pass through GET to prevention/ViewAddPreventionData with the exact failing URL parameters")
        void shouldPassThrough_forGetToViewAddPreventionDataWithParams() throws Exception {
            // Reproduces the original failing popup URL, now routed through the
            // dedicated view gate instead of the POST-only AddPrevention action:
            // /carlos/prevention/ViewAddPreventionData?4=4&prevention=Tuberculosis&demographic_no=1&prevResultDesc=
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/prevention/ViewAddPreventionData");
            when(request.getParameter("method")).thenReturn(null);
            when(request.getParameter("4")).thenReturn("4");
            when(request.getParameter("prevention")).thenReturn("Tuberculosis");
            when(request.getParameter("demographic_no")).thenReturn("1");
            when(request.getParameter("prevResultDesc")).thenReturn("");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
            verify(response, never()).sendError(anyInt());
        }
    }

    @Nested
    @DisplayName("Action name extraction")
    class ActionNameExtraction {

        @Test
        @DisplayName("should extract simple action name from path")
        void shouldExtractSimpleActionName_fromPath() {
            assertThat(HttpMethodGuardFilter.extractActionName("/tickler/addTickler"))
                    .isEqualTo("addTickler");
        }

        @Test
        @DisplayName("should extract nested action name from deep path")
        void shouldExtractNestedActionName_fromDeepPath() {
            assertThat(HttpMethodGuardFilter.extractActionName("/web/dashboard/display/AssignTickler"))
                    .isEqualTo("AssignTickler");
        }

        @Test
        @DisplayName("should extract action name without leading slash")
        void shouldExtractActionName_withoutLeadingSlash() {
            assertThat(HttpMethodGuardFilter.extractActionName("logout"))
                    .isEqualTo("logout");
        }

        @Test
        @DisplayName("should return null for non- path")
        void shouldReturnNull_forNonDoPath() {
            assertThat(HttpMethodGuardFilter.extractActionName("/some/page.jsp"))
                    .isNull();
        }

        @Test
        @DisplayName("should return null for null path")
        void shouldReturnNull_forNullPath() {
            assertThat(HttpMethodGuardFilter.extractActionName(null))
                    .isNull();
        }

        @Test
        @DisplayName("should keep action segment when earlier path segment has parameters")
        void shouldKeepActionSegment_whenEarlierPathSegmentHasParameters() {
            assertThat(HttpMethodGuardFilter.extractActionName("/billing;v=1/CA/ON/SaveBilling"))
                    .isEqualTo("SaveBilling");
        }

        @Test
        @DisplayName("should strip path parameters from mutator action segment")
        void shouldStripPathParameters_fromMutatorActionSegment() {
            assertThat(HttpMethodGuardFilter.extractActionName("/admin/ProviderDelete;jsessionid=secret"))
                    .isEqualTo("ProviderDelete");
        }
    }

    /**
     * Customize Measurements (issue #3335). The Setup* routes only load lists into the
     * session and render a form, so GET must reach them. AddMeasurementMap and
     * RemoveMeasurementMap render their page on a GET without write parameters and reject a
     * non-POST write themselves; the other write routes stay POST-only here.
     */
    @Nested
    @DisplayName("Customize Measurements routes")
    class MeasurementCustomization {

        @ParameterizedTest(name = "GET {0} renders its form")
        @ValueSource(strings = {
                "SetupAddMeasurementType",
                "SetupAddMeasuringInstruction",
                "SetupAddMeasurementGroup",
                "SetupStyleSheetList",
                "SetupGroupList",
                "SetupEditMeasurementGroup",
                "AddMeasurementMap",
                "RemoveMeasurementMap",
                "ViewAddMeasurementMap",
                "ViewRemoveMeasurementMap",
                "ViewViewMeasurementMap"
        })
        void shouldPassThrough_forGetToMeasurementFormRoute(String route) throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/encounter/oscarMeasurements/" + route);
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @ParameterizedTest(name = "GET {0} is refused")
        @ValueSource(strings = {
                "NewMeasurementMap",
                "RemapMeasurementMap",
                "AddMeasurementType",
                "AddMeasuringInstruction",
                "AddMeasurementGroup",
                "DeleteMeasurementTypes"
        })
        void shouldBlock_forGetToMeasurementWriteRoute(String route) throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/carlos/encounter/oscarMeasurements/" + route);
            when(request.getParameter("method")).thenReturn(null);

            filter.doFilter(request, response, chain);

            verify(response).sendError(anyInt(), anyString());
            verify(chain, never()).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Other HTTP methods")
    class OtherMethods {

        @Test
        @DisplayName("should pass through PUT requests")
        void shouldPassThrough_forPutRequests() throws Exception {
            when(request.getMethod()).thenReturn("PUT");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/addTickler");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should pass through DELETE requests")
        void shouldPassThrough_forDeleteRequests() throws Exception {
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestURI()).thenReturn("/carlos/tickler/addTickler");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    /**
     * Routes that take their write from a parameter other than {@code method} (issue #3394): neither the
     * route name nor {@code method} gives the write away, so the filter keys on the parameter per route.
     * Real Spring mock requests, so parameter names enumerate as the container would.
     */
    @Nested
    @DisplayName("GET requests carrying a route's own write parameter")
    class RouteWriteParameters {

        private MockHttpServletResponse doFilter(String httpMethod, String route, String... nameValuePairs)
                throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest(httpMethod, "/carlos" + route);
            req.setContextPath("/carlos");
            for (int i = 0; i < nameValuePairs.length; i += 2) {
                req.addParameter(nameValuePairs[i], nameValuePairs[i + 1]);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            return res;
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should block the privilege-row Update when it is not posted")
        void shouldBlock_forProviderPrivilegeButtonUpdate(String httpMethod) throws Exception {
            // The working exploit recorded in #2862: privilege bits rewritten from a link.
            MockHttpServletResponse res = doFilter(httpMethod, "/admin/ProviderPrivilege",
                    "buttonUpdate", "Update", "roleUserGroup", "doctor", "objectName", "_casemgmt",
                    "privileger", "on", "privilegew", "on", "priority", "5");

            assertThat(res.getStatus()).isEqualTo(405);
            assertThat(res.getHeader("Allow")).isEqualTo("POST");
            verify(chain, never()).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"submit", "buttonUpdate", "buttonSetPrimaryRole"})
        @DisplayName("should block each Provider Role write parameter, whatever its localized value")
        void shouldBlock_forProviderRoleWriteParameter(String param) throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/admin/ProviderRole", param, "Ajouter");

            assertThat(res.getStatus()).isEqualTo(405);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should block a message delete sent by GET")
        void shouldBlock_forHandleMessagesDelete() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/messenger/HandleMessages",
                    "messageNo", "1", "delete", "Delete");

            assertThat(res.getStatus()).isEqualTo(405);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should block a Select Forms reorder sent by GET")
        void shouldBlock_forFormSelectForward() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/form/select",
                    "forward", "add", "selectedAddTypes", "2 Minute Walk");

            assertThat(res.getStatus()).isEqualTo(405);
            verify(chain, never()).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should block a Teleplan file generation that is not posted")
        void shouldBlock_forGenerateTeleplanFileProviders(String httpMethod) throws Exception {
            // #4439 D3: the generation wrote an MSP batch; only its Create Report form, a POST, may.
            MockHttpServletResponse res = doFilter(httpMethod, "/billing/CA/BC/GenerateTeleplanFile", "providers", "all");

            assertThat(res.getStatus()).isEqualTo(405);
            assertThat(res.getHeader("Allow")).isEqualTo("POST");
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should pass the Teleplan Activity List's year archive, which writes nothing")
        void shouldPassThrough_forGenerateTeleplanFileYearArchive() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/billing/CA/BC/GenerateTeleplanFile", "year", "2025");

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should match the route and the parameter name case-insensitively")
        void shouldBlock_forDifferentlyCasedRouteAndParameter() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/admin/providerprivilege", "BUTTONUPDATE", "x");

            assertThat(res.getStatus()).isEqualTo(405);
        }

        @Test
        @DisplayName("should block the write parameter even when its value is empty")
        void shouldBlock_forEmptyWriteParameter() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/admin/ProviderRole", "submit", "");

            assertThat(res.getStatus()).isEqualTo(405);
        }

        @Test
        @DisplayName("should block the write parameter on a path that needs normalizing")
        void shouldBlock_forWriteParameterOnUnnormalizedPath() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/x/../admin//ProviderPrivilege;jsessionid=1",
                    "buttonUpdate", "Update");

            assertThat(res.getStatus()).isEqualTo(405);
        }

        @ParameterizedTest
        @ValueSource(strings = {"/admin/ProviderPrivilege", "/admin/ProviderRole", "/messenger/HandleMessages",
                "/billing/CA/BC/GenerateTeleplanFile"})
        @DisplayName("should pass a bare GET that only opens the page")
        void shouldPassThrough_forBareGet(String route) throws Exception {
            MockHttpServletResponse res = doFilter("GET", route);

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should pass the Provider Role filter search, which writes nothing")
        void shouldPassThrough_forProviderRoleSearch() throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/admin/ProviderRole",
                    "keyword", "FAKE", "search", "Filter");

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"reply", "replyAll", "forward"})
        @DisplayName("should pass Reply, Reply All and Forward, which only open the compose page")
        void shouldPassThrough_forHandleMessagesComposeButtons(String button) throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/messenger/HandleMessages",
                    "messageNo", "1", button, "x");

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should pass a POST that carries a write parameter")
        void shouldPassThrough_forPostedWrite() throws Exception {
            MockHttpServletResponse res = doFilter("POST", "/admin/ProviderPrivilege", "buttonUpdate", "Update");

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should not apply a route's write parameters to another route")
        void shouldPassThrough_forWriteParameterNameOnUnlistedRoute() throws Exception {
            // Log Admin's report form sends submit, and it only reads.
            MockHttpServletResponse res = doFilter("GET", "/admin/LogReport", "submit", "Search",
                    "buttonUpdate", "x", "delete", "x");

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("should label the refusal with the parameter's name and never its value")
        void shouldLabelRefusal_withWriteParameterNameOnly() throws Exception {
            try (LogCapture logs = LogCapture.forLogger(HttpMethodGuardFilter.class)) {
                doFilter("GET", "/admin/ProviderPrivilege", "buttonUpdate", "FAKE-VALUE-NOT-LOGGED");

                assertThat(logs.messages()).hasSize(1);
                assertThat(logs.messages().get(0))
                        .contains("/admin/ProviderPrivilege")
                        .contains("(write parameter buttonUpdate)")
                        .doesNotContain("FAKE-VALUE-NOT-LOGGED");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should block the Dx registry's code-description write sent without POST")
        void shouldBlock_forDxresearchEditDesc(String httpMethod) throws Exception {
            MockHttpServletResponse res = doFilter(httpMethod, "/report/DxresearchReport",
                    "method", "editDesc", "editingCodeType", "icd9", "editingCodeCode", "250",
                    "editingCodeDesc", "FAKE");

            assertThat(res.getStatus()).isEqualTo(405);
            verify(chain, never()).doFilter(any(), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"patientRegistedAll", "patientExcelReport", "addSearchCode", "clearSearchCode"})
        @DisplayName("should pass the Dx registry's report dispatches")
        void shouldPassThrough_forDxresearchReportMethods(String method) throws Exception {
            MockHttpServletResponse res = doFilter("GET", "/report/DxresearchReport", "method", method);

            assertThat(res.getStatus()).isEqualTo(200);
            verify(chain).doFilter(any(), any());
        }
    }
}
