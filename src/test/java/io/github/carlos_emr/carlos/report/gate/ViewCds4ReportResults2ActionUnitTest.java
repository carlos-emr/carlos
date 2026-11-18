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
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.commn.dao.FunctionalCentreDao;
import io.github.carlos_emr.carlos.commn.model.FunctionalCentre;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The CDS 4 report is built for one functional centre: without one it failed with HTTP 500, which the
 * CDS Report panel showed as nothing at all (issue #4885). Its gate now answers 400 first.
 */
@Tag("unit")
@Tag("report")
@DisplayName("CDS 4 report results gate requires a functional centre in use")
class ViewCds4ReportResults2ActionUnitTest extends CarlosUnitTestBase {

    private static final String CENTRE = "FC-PW-1";

    private SecurityInfoManager security;
    private FunctionalCentreDao centres;
    private LoggedInInfo login;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        centres = createAndRegisterMock(FunctionalCentreDao.class);
        login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_report"), eq("r"), isNull())).thenReturn(true);
        FunctionalCentre centre = new FunctionalCentre();
        when(centres.find(CENTRE)).thenReturn(centre);

        request = new MockHttpServletRequest("POST", "/oscarReport/ViewCds4ReportResults");
        request.setParameter("functionalCentreId", CENTRE);
        response = new MockHttpServletResponse();
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfo != null) {
            loggedInInfo.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @Test
    void shouldShowTheReport_whenTheFunctionalCentreExists() throws Exception {
        assertThat(new ViewCds4ReportResults2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAnswerBadRequest_whenNoFunctionalCentreIsPosted() throws Exception {
        for (String value : new String[]{null, "", "   "}) {
            request.removeParameter("functionalCentreId");
            if (value != null) {
                request.setParameter("functionalCentreId", value);
            }
            response = new MockHttpServletResponse();
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new ViewCds4ReportResults2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).as("status for %s", value).isEqualTo(400);
        }
    }

    @Test
    void shouldAnswerBadRequest_whenTheFunctionalCentreIsUnknown() throws Exception {
        request.setParameter("functionalCentreId", "NO-SUCH-CENTRE");

        assertThat(new ViewCds4ReportResults2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRefuseTheReport_withoutReportRead() {
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> new ViewCds4ReportResults2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldAnswerBadRequest_throughTheGateTheRouteIsMappedTo() throws Exception {
        // The route's mapped class, read from the Struts configuration: the shared report gate let the
        // request through to the page, which failed with HTTP 500 without a functional centre.
        String mapping = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-report.xml"));
        Matcher action = Pattern.compile("<action name=\"oscarReport/ViewCds4ReportResults\" class=\"([^\"]+)\"").matcher(mapping);
        assertThat(action.find()).as("oscarReport/ViewCds4ReportResults is mapped").isTrue();
        request.removeParameter("functionalCentreId");

        ActionSupport gate = (ActionSupport) Class.forName(action.group(1)).getDeclaredConstructor().newInstance();

        assertThat(gate.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
