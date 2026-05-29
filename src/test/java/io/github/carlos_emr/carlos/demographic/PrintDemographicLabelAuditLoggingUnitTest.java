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
package io.github.carlos_emr.carlos.demographic;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStream;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Print demographic label audit logging")
@Tag("unit")
@Tag("demographic")
@Isolated
class PrintDemographicLabelAuditLoggingUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final String DEMOGRAPHIC_NO = "12345";
    private static final String REMOTE_ADDR = "127.0.0.1";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<LegacyJdbcQuery> legacyJdbcQueryMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private Connection connection;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE_ADDR);
        request.setParameter("demographic_no", DEMOGRAPHIC_NO);
        request.getSession().getServletContext().setAttribute("org.apache.catalina.jsp_classpath", "test-classpath");

        response = new MockHttpServletResponse();

        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        connection = mock(Connection.class);

        Provider provider = mock(Provider.class);
        ProgramManager2 programManager2 = mock(ProgramManager2.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(ProgramManager2.class, programManager2);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(provider.getProviderNo()).thenReturn(PROVIDER_NO);
        when(programManager2.getCurrentProgramInDomain(loggedInInfo, PROVIDER_NO)).thenReturn((ProgramProvider) null);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        legacyJdbcQueryMock = mockStatic(LegacyJdbcQuery.class);
        legacyJdbcQueryMock.when(LegacyJdbcQuery::getConnection).thenReturn(connection);
    }

    @AfterEach
    void tearDown() {
        if (legacyJdbcQueryMock != null) {
            legacyJdbcQueryMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    void shouldAuditDemographicRead_whenPrintingDemographicLabel() throws Exception {
        assertThat(execute(new PrintDemoLabel2Action())).isEqualTo(ActionSupport.NONE);
    }

    @Test
    void shouldAuditDemographicRead_whenPrintingAddressLabel() throws Exception {
        assertThat(execute(new PrintDemoAddressLabel2Action())).isEqualTo(ActionSupport.NONE);
    }

    @Test
    void shouldAuditDemographicRead_whenPrintingChartLabel() throws Exception {
        assertThat(execute(new PrintDemoChartLabel2Action())).isEqualTo(ActionSupport.NONE);
    }

    @Test
    void shouldAuditDemographicRead_whenPrintingClientLabLabel() throws Exception {
        assertThat(execute(new PrintClientLabLabel2Action())).isEqualTo(ActionSupport.NONE);
    }

    private String execute(ActionSupport action) throws Exception {
        try (MockedConstruction<OscarDocumentCreator> ignored = mockConstruction(OscarDocumentCreator.class,
                (mock, context) -> doNothing().when(mock).fillDocumentStream(
                        anyMap(),
                        any(),
                        eq("pdf"),
                        nullable(InputStream.class),
                        eq(connection),
                        nullable(String.class)))) {
            String result = action.execute();
            logActionMock.verify(() -> LogAction.addLog(
                    PROVIDER_NO,
                    LogConst.READ,
                    LogConst.CON_DEMOGRAPHIC,
                    DEMOGRAPHIC_NO,
                    REMOTE_ADDR,
                    DEMOGRAPHIC_NO));
            return result;
        }
    }
}
