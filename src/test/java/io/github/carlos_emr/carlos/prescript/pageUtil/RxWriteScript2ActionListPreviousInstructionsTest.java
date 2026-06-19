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

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RxWriteScript2Action listPreviousInstructions")
@Tag("unit")
@Tag("prescript")
class RxWriteScript2ActionListPreviousInstructionsTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private UserPropertyDAO mockUserPropertyDAO;
    @Mock
    private PartialDateDao mockPartialDateDao;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private RxManager mockRxManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private HttpSession mockSession;

    private RxSessionBean bean;
    private RxWriteScript2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(UserPropertyDAO.class, mockUserPropertyDAO);
        registerMock(PartialDateDao.class, mockPartialDateDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(RxManager.class, mockRxManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockRequest.getSession()).thenReturn(mockSession);

        bean = new RxSessionBean();
        bean.setListMedHistory(new ArrayList<>(List.of(historyEntry("take once daily"))));
        when(mockSession.getAttribute("RxSessionBean")).thenReturn(bean);

        action = new RxWriteScript2Action();
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

    @Test
    @DisplayName("should return NONE and send bad request when randomId contains non-digits")
    void shouldReturnNone_whenRandomIdContainsNonDigits() throws Exception {
        when(mockRequest.getParameter("randomId")).thenReturn("12\r\ninvalid");

        String result = action.listPreviousInstructions();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(bean.getListMedHistory()).isEmpty();
        verify(mockResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid randomId: digits only");
    }

    @Test
    @DisplayName("should return NONE and send not found when stash item is missing")
    void shouldReturnNone_whenStashItemIsMissing() throws Exception {
        when(mockRequest.getParameter("randomId")).thenReturn("42");

        String result = action.listPreviousInstructions();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(bean.getListMedHistory()).isEmpty();
        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "Prescription not found for randomId: 42");
    }

    @Test
    @DisplayName("should populate previous instructions when randomId matches a stash item")
    void shouldPopulatePreviousInstructions_whenRandomIdMatchesStashItem() throws Exception {
        RxPrescriptionData.Prescription prescription = new RxPrescriptionData.Prescription(0, "999998", 123);
        prescription.setRandomId(42);
        bean.getStashList().add(prescription);
        List<HashMap<String, String>> history = new ArrayList<>(List.of(historyEntry("take twice daily")));
        when(mockRequest.getParameter("randomId")).thenReturn("42");

        try (MockedStatic<RxUtil> rxUtilMock = mockStatic(RxUtil.class)) {
            rxUtilMock.when(() -> RxUtil.getPreviousInstructions(prescription)).thenReturn(history);

            String result = action.listPreviousInstructions();

            assertThat(result).isNull();
            assertThat(bean.getListMedHistory()).containsExactlyElementsOf(history);
            verify(mockResponse, never()).sendError(anyInt(), any(String.class));
        }
    }

    private HashMap<String, String> historyEntry(String instruction) {
        HashMap<String, String> entry = new HashMap<>();
        entry.put("instruction", instruction);
        return entry;
    }
}
