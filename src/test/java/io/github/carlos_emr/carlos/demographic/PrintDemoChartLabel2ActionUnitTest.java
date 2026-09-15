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
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.HashMap;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("PrintDemoChartLabel2Action")
@Tag("unit")
@Tag("demographic")
class PrintDemoChartLabel2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldUseClasspathTemplate_whenHomeTemplateIsNotTrusted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setParameter("demographic_no", "12345");
        request.getSession()
                .getServletContext()
                .setAttribute("org.apache.catalina.jsp_classpath", "");
        String classpathProperty = "jasper.reports.compile.class.path";
        String originalClasspath = System.getProperty(classpathProperty);

        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        UserPropertyDAO userPropertyDao = createAndRegisterMock(UserPropertyDAO.class);
        ProgramManager2 programManager = createAndRegisterMock(ProgramManager2.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Provider provider = mock(Provider.class);
        Connection connection = mock(Connection.class);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(provider.getProviderNo()).thenReturn("999998");
        when(userPropertyDao.getProp(any(), any())).thenReturn(null);
        when(programManager.getCurrentProgramInDomain(loggedInInfo, "999998")).thenReturn(null);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
                MockedStatic<LoggedInInfo> loggedInInfoContext = mockStatic(LoggedInInfo.class);
                MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class);
                MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class);
                MockedConstruction<OscarDocumentCreator> documentCreators =
                        mockConstruction(OscarDocumentCreator.class)) {
            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            loggedInInfoContext
                    .when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            pathValidation
                    .when(() -> PathValidationUtils.resolveTrustedPath(any(File.class)))
                    .thenThrow(new SecurityException("not trusted"));
            legacyJdbcQuery.when(LegacyJdbcQuery::getConnection).thenReturn(connection);

            assertThat(new PrintDemoChartLabel2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(documentCreators.constructed()).hasSize(1);
            verify(documentCreators.constructed().get(0))
                    .fillDocumentStream(
                            any(HashMap.class),
                            any(OutputStream.class),
                            eq("pdf"),
                            any(InputStream.class),
                            same(connection),
                            isNull());
        } finally {
            if (originalClasspath == null) {
                System.clearProperty(classpathProperty);
            } else {
                System.setProperty(classpathProperty, originalClasspath);
            }
        }

        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }
}
