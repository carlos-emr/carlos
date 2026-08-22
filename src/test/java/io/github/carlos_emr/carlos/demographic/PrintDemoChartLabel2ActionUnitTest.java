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
package io.github.carlos_emr.carlos.demographic;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PrintDemoChartLabel2Action")
@Tag("unit")
@Tag("demographic")
class PrintDemoChartLabel2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private UserPropertyDAO userPropertyDao;
    private ProgramManager2 programManager;
    private LoggedInInfo loggedInInfo;
    private Provider provider;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("demographic_no", "12345");
        request.getSession().getServletContext().setAttribute("org.apache.catalina.jsp_classpath", "");

        securityInfoManager = mock(SecurityInfoManager.class);
        userPropertyDao = mock(UserPropertyDAO.class);
        programManager = mock(ProgramManager2.class);
        loggedInInfo = mock(LoggedInInfo.class);
        provider = mock(Provider.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(UserPropertyDAO.class, userPropertyDao);
        registerMock(ProgramManager2.class, programManager);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(provider.getProviderNo()).thenReturn("999998");
        when(programManager.getCurrentProgramInDomain(loggedInInfo, "999998"))
                .thenReturn((ProgramProvider) null);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    void shouldUseClasspathLabel_whenTrustedPathResolutionThrowsSecurityException() throws Exception {
        Connection connection = mock(Connection.class);

        try (MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
                MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class);
                MockedConstruction<OscarDocumentCreator> documentCreators = mockConstruction(OscarDocumentCreator.class)) {
            pathValidation.when(() -> PathValidationUtils.resolveTrustedPath(any(File.class)))
                    .thenThrow(new SecurityException("blocked"));
            legacyJdbcQuery.when(LegacyJdbcQuery::getConnection).thenReturn(connection);

            String result = new PrintDemoChartLabel2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(documentCreators.constructed()).hasSize(1);

            @SuppressWarnings({"unchecked", "rawtypes"})
            ArgumentCaptor<HashMap> parametersCaptor = ArgumentCaptor.forClass(HashMap.class);
            ArgumentCaptor<InputStream> templateCaptor = ArgumentCaptor.forClass(InputStream.class);
            verify(documentCreators.constructed().get(0)).fillDocumentStream(
                    parametersCaptor.capture(),
                    any(OutputStream.class),
                    eq("pdf"),
                    templateCaptor.capture(),
                    eq(connection),
                    isNull());
            assertThat(parametersCaptor.getValue()).containsEntry("demo", "12345");
            assertThat(templateCaptor.getValue()).isNotNull();
        }
    }
}
