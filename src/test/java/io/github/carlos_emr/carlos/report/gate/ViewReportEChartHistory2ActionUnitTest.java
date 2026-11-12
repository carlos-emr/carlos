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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * ECHART HISTORY lists one patient's eChart entries, so its gate must honour that patient's record
 * restrictions, not only {@code _report r}.
 */
@Tag("unit")
@Tag("report")
@DisplayName("ECHART HISTORY gate enforces the patient's record restrictions")
class ViewReportEChartHistory2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "123";

    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_report"), eq("r"), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt())).thenReturn(true);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), anyString())).thenReturn(true);

        request = new MockHttpServletRequest("GET", "/report/ViewReportecharthistory");
        request.setParameter("demographic_no", PATIENT);
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
    void shouldShowHistory_whenThePatientRecordIsAccessible() throws Exception {
        assertThat(new ViewReportEChartHistory2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldRefuseHistory_whenThePatientRecordIsLocked() {
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(false);

        assertThatThrownBy(() -> new ViewReportEChartHistory2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseHistory_whenDemographicReadIsDeniedForThePatient() {
        when(security.hasPrivilege(login, "_demographic", "r", PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> new ViewReportEChartHistory2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRefuseHistory_withoutReportRead() {
        when(security.hasPrivilege(login, "_report", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> new ViewReportEChartHistory2Action().execute()).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldAnswerBadRequest_whenThePatientNumberIsMissingOrMalformed() throws Exception {
        for (String value : new String[]{null, "", "0", "-1", "12a", "99999999999"}) {
            request.removeParameter("demographic_no");
            if (value != null) {
                request.setParameter("demographic_no", value);
            }
            response = new MockHttpServletResponse();
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            assertThat(new ViewReportEChartHistory2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).as("status for %s", value).isEqualTo(400);
        }
    }

    @Test
    void shouldRefuseALockedPatient_throughTheGateTheRouteIsMappedTo() throws Exception {
        // The route's mapped class, read from the Struts configuration: the shared report gate
        // checked only _report r, so it let a locked patient's history through.
        String mapping = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-report.xml"));
        Matcher action = Pattern.compile("<action name=\"report/ViewReportecharthistory\" class=\"([^\"]+)\"").matcher(mapping);
        assertThat(action.find()).as("report/ViewReportecharthistory is mapped").isTrue();
        when(security.isAllowedAccessToPatientRecord(login, 123)).thenReturn(false);
        when(security.hasPrivilege(login, "_demographic", "r", PATIENT)).thenReturn(false);

        ActionSupport gate = (ActionSupport) Class.forName(action.group(1)).getDeclaredConstructor().newInstance();

        assertThatThrownBy(gate::execute).isInstanceOf(SecurityException.class);
    }
}
