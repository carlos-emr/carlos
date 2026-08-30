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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    /** Denies every privilege, so execute() always stops at the gate and never touches DrugRef. */
    private void denyAllPrivileges() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), any(), isNull()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("should demand write privilege when method is updateDB")
    void shouldDemandWrite_whenMethodIsUpdateDb() {
        denyAllPrivileges();
        mockRequest.setParameter("method", "updateDB");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "w", null);
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
        // The status probe is a GET on every Rx page load, so the guard above must not catch it.
        denyAllPrivileges();
        mockRequest.setMethod("GET");
        mockRequest.setParameter("method", "verify");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }

    @Test
    @DisplayName("should not treat a case variant of updateDB as the mutating method")
    void shouldNotTreatCaseVariant_asMutatingMethod() {
        denyAllPrivileges();
        // The dispatch below is String.equals, so a case variant must NOT select updateDB.
        // The privilege decision and the dispatch read the same expression, so this asserts
        // they cannot diverge: whatever is not exactly "updateDB" is gated at read AND routed
        // to a read-only branch. A future refactor that lowered the gate to "r" while leaving a
        // looser dispatch would reach the mutation at read privilege, and this fails.
        mockRequest.setParameter("method", "UPDATEDB");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "r", null);
    }
}
