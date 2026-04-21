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

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
            when(request.getParameter("method")).thenReturn(null);
            when(request.getParameter("4")).thenReturn("4");
            when(request.getParameter("prevention")).thenReturn("Tuberculosis");
            when(request.getParameter("demographic_no")).thenReturn("1");
            when(request.getParameter("prevResultDesc")).thenReturn("");
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
}
