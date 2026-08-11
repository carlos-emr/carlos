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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link EctMeasurements2Action}.
 */
@DisplayName("EctMeasurements2Action")
@Tag("unit")
@Tag("clinical")
class EctMeasurements2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private EctMeasurements2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/encounter/Measurements");
        response = new MockHttpServletResponse();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        MeasurementTypeDao measurementTypeDao = mock(MeasurementTypeDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(MeasurementTypeDao.class, measurementTypeDao);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", null)).thenReturn(true);
        when(measurementTypeDao.findByTypeAndMeasuringInstruction(any(), any())).thenReturn(List.of());

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        action = new TestableEctMeasurements2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
    }

    @Test
    @DisplayName("should not propagate a request-controlled stylesheet after validation failure")
    void shouldNotPropagateStylesheet_afterValidationFailure() throws Exception {
        request.setParameter("demographicNo", "123");
        request.setParameter("numType", "1");
        request.setParameter("parentChanged", "false");
        request.setParameter("inputValue-0", "1");
        request.setParameter("inputType-0", "TEST");
        request.setParameter("inputTypeDisplayName-0", "Test measurement");
        request.setParameter("inputMInstrc-0", "NA");
        request.setParameter("comments-0", "");
        request.setParameter("date-0", "invalid-date");
        request.setParameter("css", "javascript:alert('measurements')");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/encounter/oscarMeasurements/ViewAddMeasurementData");
        assertThat(request.getAttribute("css")).isNull();
    }

    private static final class TestableEctMeasurements2Action extends EctMeasurements2Action {

        @Override
        public String getText(String key) {
            return key;
        }

        @Override
        public String getText(String key, String[] args) {
            return key;
        }
    }
}
