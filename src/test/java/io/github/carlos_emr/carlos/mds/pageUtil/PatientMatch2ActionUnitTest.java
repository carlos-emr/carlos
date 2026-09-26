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
package io.github.carlos_emr.carlos.mds.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.service.MrpRoutingService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("lab")
@DisplayName("PatientMatch2Action")
class PatientMatch2ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private MrpRoutingService mrpRouting;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<CommonLabResultData> labResults;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        mrpRouting = mock(MrpRoutingService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        request = new MockHttpServletRequest("POST", "/oscarMDS/PatientMatch");
        request.setContextPath("/carlos");
        request.setParameter("labNo", "555");
        request.setParameter("labType", "HL7");
        request.setParameter("demographicNo", "42");
        response = new MockHttpServletResponse();

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        labResults = mockStatic(CommonLabResultData.class);
    }

    @AfterEach
    void tearDown() {
        labResults.close();
        login.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 to a read method before any routing change")
    void shouldRejectReadMethod_beforeAnyRoutingChange(String method) throws Exception {
        request.setMethod(method);

        assertThat(new PatientMatch2Action(mrpRouting).execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(security, mrpRouting);
        labResults.verifyNoInteractions();
    }

    @Test
    @DisplayName("should require _lab write")
    void shouldThrowSecurityException_whenLabWriteMissing() {
        assertThatThrownBy(() -> new PatientMatch2Action(mrpRouting).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");
        labResults.verifyNoInteractions();
        verifyNoInteractions(mrpRouting);
    }

    @Test
    @DisplayName("should apply provider linking rules after a saved match")
    void shouldRouteToMrp_afterSavedMatch() throws Exception {
        when(security.hasPrivilege(loggedInInfo, "_lab", "w", null)).thenReturn(true);
        labResults.when(() -> CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).thenReturn(true);

        assertThat(new PatientMatch2Action(mrpRouting).execute()).isEqualTo(ActionSupport.NONE);

        verify(mrpRouting).routeMatchedLabToMrp("555", "HL7", 42, "999998");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/oscarMDS/ViewOpenEChart?demographicNo=42");
    }

    @Test
    @DisplayName("should not widen who sees a lab whose match failed")
    void shouldNotRouteToMrp_whenMatchFails() throws Exception {
        when(security.hasPrivilege(loggedInInfo, "_lab", "w", null)).thenReturn(true);
        labResults.when(() -> CommonLabResultData.updatePatientLabRouting(anyString(), anyString(), anyString()))
                .thenReturn(false);

        new PatientMatch2Action(mrpRouting).execute();

        verify(mrpRouting, never()).routeMatchedLabToMrp(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("should keep the match and open the chart when MRP routing fails")
    void shouldStillOpenChart_whenMrpRoutingThrows() throws Exception {
        when(security.hasPrivilege(loggedInInfo, "_lab", "w", null)).thenReturn(true);
        labResults.when(() -> CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).thenReturn(true);
        doThrow(new IllegalStateException("database down"))
                .when(mrpRouting).routeMatchedLabToMrp(eq("555"), eq("HL7"), eq(42), eq("999998"));

        assertThat(new PatientMatch2Action(mrpRouting).execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/oscarMDS/ViewOpenEChart?demographicNo=42");
    }
}
