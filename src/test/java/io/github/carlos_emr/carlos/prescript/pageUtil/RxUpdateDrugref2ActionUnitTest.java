/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests pinning the per-method privilege split on {@link RxUpdateDrugref2Action}.
 *
 * <p>This action used to demand {@code _rx} write for every method, before dispatching. That
 * gated the read-only {@code verify} status probe -- which TopLinks2.jspf fires on every Rx page
 * load -- on write, so a read-only prescriber got a SecurityException, an HTML 500 in place of
 * JSON, and a permanent "Drugref database is unavailable" banner with DrugRef perfectly healthy.
 *
 * <p>The fix follows the privilege from the method, which makes the split itself
 * security-relevant: the risk of getting it wrong is a mutation reachable at read privilege.
 * These tests assert both directions -- that {@code updateDB} still requires write, and that the
 * read-only methods no longer do -- and that an absent or unrecognised {@code method} falls to a
 * read, still behind a privilege check rather than through it.
 *
 * @since 2026-08-30
 */
@DisplayName("RxUpdateDrugref2Action privilege split")
@Tag("unit")
@Tag("rx")
class RxUpdateDrugref2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxUpdateDrugref2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        action = new RxUpdateDrugref2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Denies every privilege, so execute() always stops at the gate and never touches DrugRef.
     *
     * <p>Explicit rather than relying on Mockito's false default: it documents the precondition
     * these tests depend on, and it fails loudly if the stubbed overload ever stops matching
     * (SecurityInfoManager has two hasPrivilege overloads).
     */
    private void denyAllPrivileges() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), any(), isNull()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("should demand administration rights when method is updateDB")
    void shouldDemandAdministrationRights_whenMethodIsUpdateDb() {
        // A rebuild is an administrative act, so it is gated like the page that offers it and
        // NOT on _rx. Requiring both locked out an administrator who is not a prescriber.
        denyAllPrivileges();
        mockRequest.setParameter("method", "updateDB");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin or _admin.misc)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_admin", "r", null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_rx"), any(), isNull());
    }

    @Test
    @DisplayName("should demand only read privilege when method is verify")
    void shouldDemandOnlyRead_whenMethodIsVerify() {
        denyAllPrivileges();
        mockRequest.setParameter("method", "verify");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }

    @Test
    @DisplayName("should demand only read privilege when method is absent")
    void shouldDemandOnlyRead_whenMethodIsAbsent() {
        denyAllPrivileges();

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }

    @Test
    @DisplayName("should reject GET when method is updateDB, before any privilege check")
    void shouldRejectGet_whenMethodIsUpdateDb() throws Exception {
        // updateDB rebuilds the DrugRef database. Reachable by GET it is a CSRF target — a link
        // or an <img src> triggers a full rebuild, and CSRFGuard's token check does not cover
        // GET. The rejection must come before the privilege check so that no side effect, and
        // no privilege probe, hangs off the wrong method.
        mockRequest.setMethod("GET");
        mockRequest.setParameter("method", "updateDB");

        action.execute();

        assertThat(mockResponse.getStatus())
                .isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should still allow GET for the read-only status methods")
    void shouldAllowGet_forReadOnlyMethods() {
        // The status probe posts today (TopLinks2.jspf), but the read-only methods are
        // deliberately left reachable by GET: they mutate nothing, and narrowing them would
        // break any caller that reads status with a plain GET.
        denyAllPrivileges();
        mockRequest.setMethod("GET");
        mockRequest.setParameter("method", "verify");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }

    @Test
    @DisplayName("should demand administration rights when method is status")
    void shouldDemandAdministrationRights_whenMethodIsStatus() {
        denyAllPrivileges();
        mockRequest.setParameter("method", "status");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin or _admin.misc)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_admin", "r", null);
    }

    @Test
    @DisplayName("should refuse status to a prescriber without administration rights")
    void shouldRefuseStatus_whenTheCallerHasNoAdminRights() throws Exception {
        // `status` relays DrugRef's root-cause failure text -- a JDBC URL, a database host and
        // user, a filesystem path. `_rx` read is every prescriber in the clinic; the page that
        // consumes this is gated on _admin / _admin.misc read by ViewUpdateDrugref2Action.
        // Reporting is not automatically public just because it does not write.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.misc"), eq("r"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("method", "status");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin or _admin.misc");
        assertThat(mockResponse.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should allow status on the misc administration right alone")
    void shouldAllowStatus_whenTheCallerHasOnlyAdminMisc() throws Exception {
        // ViewUpdateDrugref2Action accepts either right, so this must too -- gating on _admin
        // alone would lock the relay away from operators who can open the page.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.misc"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "status");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).contains("\"state\":\"UNAVAILABLE\"");
    }

    @Test
    @DisplayName("should still allow verify at prescriber read rights")
    void shouldStillAllowVerify_atPrescriberReadRights() throws Exception {
        // The counterpart to the two above: TopLinks2.jspf fires verify on every Rx page load,
        // and it carries only a date, a version and a database name. Tightening status must not
        // drag verify along with it, or every prescriber gets the "unavailable" banner again.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "verify");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).startsWith("application/json");
        // No administration rights needed: prescriber read alone is enough.
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }

    @Test
    @DisplayName("should not query administration rights when prescriber rights already allow verify")
    void shouldNotQueryAdministrationRights_whenRxRightsAlreadyAllowVerify() throws Exception {
        // Ordering, not just outcome. Every hasPrivilege call re-runs
        // secUserRoleDao.findActiveByProviderNo -- there is no role cache -- and TopLinks2.jspf
        // fires verify on every Rx page load. Evaluating the administration rights first cost
        // ordinary prescribers two extra role queries per page load to answer a question `_rx`
        // alone settles, so the short-circuit is the point and this pins it.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "verify");

        action.execute();

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_admin"), any(), isNull());
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_admin.misc"), any(), isNull());
    }

    @Test
    @DisplayName("should allow verify to an administrator who is not a prescriber")
    void shouldAllowVerify_forAnAdministratorWithoutRxRights() throws Exception {
        // The admin page fires verify too. Gating it on _rx alone would leave an administrator
        // who does not prescribe able to open the page and see nothing but an outage banner.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), any(), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "verify");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).startsWith("application/json");
    }

    @Test
    @DisplayName("should let an administrator without Rx rights start a rebuild")
    void shouldAllowUpdateDb_forAnAdministratorWithoutRxRights() throws Exception {
        // The defect this replaces: _rx was required on top of _admin, so a non-prescribing
        // administrator could open the page and every call from it failed with an HTML 500.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), any(), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("method", "updateDB");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).contains("result");
    }

    @Test
    @DisplayName("should answer status JSON with state UNAVAILABLE when DrugRef cannot be reached")
    void shouldAnswerUnavailableState_whenDrugRefUnreachable() throws Exception {
        // No DrugRef is listening in a unit test, so the relay must degrade to a well-formed
        // JSON payload the page can act on, not an HTTP 500. UNAVAILABLE is also what a DrugRef
        // build without getUpdateStatus produces (an XML-RPC fault), and the page falls back to
        // the verify probe on it.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        // status also demands administration rights: it relays DrugRef's root-cause text.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "status");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).startsWith("application/json");
        String body = mockResponse.getContentAsString();
        assertThat(body).contains("\"state\":\"UNAVAILABLE\"");
        // The outage payload must carry the SAME keys as a successful answer. A client meeting
        // the documented set on the success path and a bare state here would have to cope with a
        // shape change on exactly the path where it knows least.
        assertThat(body).contains("\"step\":\"\"")
                .contains("\"message\":\"\"")
                .contains("\"startedAt\":\"\"")
                .contains("\"finishedAt\":\"\"")
                .contains("\"lastUpdate\":\"\"");
        // status is gated on administration rights alone; _rx is not consulted for it.
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_admin", "r", null);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_rx"), any(), isNull());
        verifyNoMoreInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should answer a null result as JSON when updateDB cannot reach DrugRef")
    void shouldAnswerNullResultAsJson_whenUpdateDbUnreachable() throws Exception {
        // The page renders {"result":null} as "could not be started"; it used to render nothing.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        // A rebuild is an administrative act: it also needs the page's right.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("method", "updateDB");

        String result = action.execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).isEqualTo("{\"result\":null}");
    }

    @Test
    @DisplayName("should refuse to start a rebuild for a prescriber without administration rights")
    void shouldRefuseUpdateDb_whenTheCallerHasOnlyRxWrite() throws Exception {
        // `_rx` write is every prescriber in the clinic. A rebuild degrades prescribing for
        // half an hour and is an administrative act; the page that offers it is gated on
        // _admin / _admin.misc, and the direct POST must be gated the same way or the page's
        // gate is decorative.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin.misc"), eq("r"), isNull()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("method", "updateDB");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin or _admin.misc");
        assertThat(mockResponse.getContentAsString()).as("nothing reached the response").isEmpty();
    }

    @Test
    @DisplayName("should gate a case variant of updateDB at read, not write")
    void shouldGateCaseVariant_atReadPrivilege() {
        denyAllPrivileges();
        // Scope note: this pins the GATE only. Denying every privilege makes execute() throw at
        // the gate, before the dispatch below it, so this test cannot observe which branch
        // "UPDATEDB" routes to -- shouldRouteCaseVariant_toAReadOnlyBranch covers that half.
        mockRequest.setParameter("method", "UPDATEDB");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        // The read branch consults administration rights first (they also satisfy it), then _rx.
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_admin", "r", null);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_admin.misc", "r", null);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
        verifyNoMoreInteractions(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should route a case variant of updateDB to a read-only branch, not the rebuild")
    void shouldRouteCaseVariant_toAReadOnlyBranch() throws Exception {
        // The other half, and the one that matters: GRANT read, DENY write, then let execute()
        // run past the gate and actually dispatch. The gate and the dispatch both derive from
        // the same `mutating` expression today, but nothing structural forces that -- a refactor
        // that loosened only the dispatch (equalsIgnoreCase, or an alias) would let a read-only
        // prescriber trigger a full DrugRef rebuild while every gate assertion stayed green.
        // Assert on the response body: getLastUpdate answers {"lastUpdate":...}, updateDB
        // answers {"result":...}.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("method", "UPDATEDB");

        action.execute();

        assertThat(mockResponse.getContentAsString())
                .as("a case variant must reach a read-only status branch, never the rebuild")
                .doesNotContain("\"result\"");
    }
}
