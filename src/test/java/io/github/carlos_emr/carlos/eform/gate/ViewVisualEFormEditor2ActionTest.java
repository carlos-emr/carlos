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
package io.github.carlos_emr.carlos.eform.gate;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewVisualEFormEditor2Action}, the gate that opens the Visual eForm Editor
 * blank or pre-loaded with {@code ?fid=<id>} (issue #3166).
 *
 * @since 2026-10-22
 */
@DisplayName("ViewVisualEFormEditor2Action")
@Tag("unit")
@Tag("eform")
class ViewVisualEFormEditor2ActionTest extends CarlosUnitTestBase {

    private static final String EDITOR_VIEW = "/WEB-INF/jsp/eform/visualEformEditor.jsp";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private EFormDao mockEFormDao;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private RequestDispatcher mockDispatcher;

    private ViewVisualEFormEditor2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockRequest.getRequestDispatcher(EDITOR_VIEW)).thenReturn(mockDispatcher);

        action = new ViewVisualEFormEditor2Action(mockEFormDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    private static EForm storedForm(boolean current) {
        EForm eForm = new EForm();
        eForm.setCurrent(current);
        return eForm;
    }

    @Test
    @DisplayName("should open the editor blank when no fid is given")
    void shouldForwardBlankEditor_whenNoFidProvided() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDispatcher).forward(mockRequest, mockResponse);
        verify(mockRequest, never()).setAttribute(eq(ViewVisualEFormEditor2Action.INITIAL_FID_ATTRIBUTE), any());
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should treat an empty fid as opening the editor blank")
    void shouldForwardBlankEditor_whenFidIsEmpty() throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn("");

        action.execute();

        verify(mockDispatcher).forward(mockRequest, mockResponse);
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should pass the parsed id of a current eForm to the page")
    void shouldExposeInitialFid_whenFormIsCurrent() throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn("42");
        when(mockEFormDao.findById(42)).thenReturn(storedForm(true));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockRequest).setAttribute(ViewVisualEFormEditor2Action.INITIAL_FID_ATTRIBUTE, 42);
        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "01", "12 ", "<script>", "2147483648", "99999999999",
            "99999999999999999999"})
    @DisplayName("should reject a fid that is not a positive decimal id before any lookup")
    void shouldRejectMalformedFid_withBadRequest(String rawFid) throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn(rawFid);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockEFormDao, mockDispatcher);
        verify(mockRequest, never()).setAttribute(anyString(), any());
    }

    @Test
    @DisplayName("should accept the largest id an eForm can have")
    void shouldLookUpTenDigitFid_atIntegerMaximum() throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn("2147483647");
        when(mockEFormDao.findById(Integer.MAX_VALUE)).thenReturn(storedForm(true));

        action.execute();

        verify(mockRequest).setAttribute(ViewVisualEFormEditor2Action.INITIAL_FID_ATTRIBUTE, Integer.MAX_VALUE);
        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("should refuse to pre-load a form for a user who cannot save eForms")
    void shouldThrowSecurityException_whenPreloadingWithoutEFormWrite() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockRequest.getParameter("fid")).thenReturn("42");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eform)");
        verifyNoInteractions(mockEFormDao, mockDispatcher);
    }

    @Test
    @DisplayName("should still open a blank editor for an eForm administrator without eForm write")
    void shouldForwardBlankEditor_withoutEFormWrite() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(false);

        action.execute();

        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("should return 404 when no eForm has the id")
    void shouldReturnNotFound_whenFormDoesNotExist() throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn("7");
        when(mockEFormDao.findById(7)).thenReturn(null);

        action.execute();

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(mockDispatcher);
    }

    @Test
    @DisplayName("should return 404 for a deleted eForm")
    void shouldReturnNotFound_whenFormIsDeleted() throws Exception {
        when(mockRequest.getParameter("fid")).thenReturn("8");
        when(mockEFormDao.findById(8)).thenReturn(storedForm(false));

        action.execute();

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(mockDispatcher);
    }

    @Test
    @DisplayName("should require eForm administration write before reading the fid")
    void shouldThrowSecurityException_withoutAdminEFormWrite() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull()))
                .thenReturn(false);
        when(mockRequest.getParameter("fid")).thenReturn("42");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.eform)");
        verifyNoInteractions(mockEFormDao, mockDispatcher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("should stay a read-only page that only answers GET and HEAD")
    void shouldRejectNonReadMethods_withMethodNotAllowed(String method) throws Exception {
        when(mockRequest.getMethod()).thenReturn(method);
        when(mockRequest.getParameter("fid")).thenReturn("42");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).setHeader("Allow", "GET, HEAD");
        verify(mockResponse).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockEFormDao, mockDispatcher);
    }

    @Test
    @DisplayName("should serve HEAD like GET")
    void shouldForwardEditor_forHeadRequest() throws Exception {
        when(mockRequest.getMethod()).thenReturn("HEAD");

        action.execute();

        verify(mockDispatcher).forward(mockRequest, mockResponse);
    }
}
