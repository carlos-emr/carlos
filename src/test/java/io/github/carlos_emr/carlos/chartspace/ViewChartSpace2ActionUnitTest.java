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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewChartSpace2Action}; no Spring context.
 *
 * @since 2026-10-08
 */
@Tag("unit")
class ViewChartSpace2ActionUnitTest {

    private static final String SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private LoggedInInfo info;
    private SecurityInfoManager securityInfoManager;
    private CaseManagementManager caseManagementManager;
    private DemographicManager demographicManager;
    private ViewChartSpace2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
        request.setMethod("GET");
        info = mock(LoggedInInfo.class);
        session.setAttribute(SESSION_KEY, info);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        securityInfoManager = mock(SecurityInfoManager.class);
        caseManagementManager = mock(CaseManagementManager.class);
        // Everything the validator checks after _eChart r is granted here; each test still grants _eChart itself.
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), eq("2"))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(2))).thenReturn(true);
        when(caseManagementManager.isClientInProgramDomain("999998", "2")).thenReturn(true);
        demographicManager = mock(DemographicManager.class);
        when(demographicManager.getDemographic(any(), eq(2))).thenReturn(fakePatient());
        action = new ViewChartSpace2Action(new ChartSpaceRequestValidator(securityInfoManager, caseManagementManager),
                demographicManager);
    }

    private static Demographic fakePatient() {
        Demographic d = new Demographic(2, "FAKE-Given", "FAKE-Family");
        d.setYearOfBirth("1980");
        d.setMonthOfBirth("01");
        d.setDateOfBirth("01");
        return d;
    }

    @AfterEach
    void tearDown() {
        ActionContext.clear();
    }

    @Test
    void shouldReturnSuccess_whenEChartReadGrantedForPatient() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("demographicNo")).isEqualTo(2);
    }

    @Test
    void shouldThrowSecurityException_whenEChartReadMissing() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
    }

    @Test
    void shouldThrowSecurityException_whenNoLoggedInInfo() {
        session.removeAttribute(SESSION_KEY);
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldReturn400_whenDemographicNoInvalid() throws Exception {
        request.setParameter("demographicNo", "abc");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(securityInfoManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldReturn405_forNonGetMethods(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "2");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldReturnSuccess_forHead() throws Exception {
        request.setMethod("HEAD");
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldSetPatientNameAndAge_whenPatientExists() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("csPatientName")).isEqualTo("FAKE-Family, FAKE-Given");
        assertThat((String) request.getAttribute("csPatientAge")).isNotBlank();
    }

    @Test
    void shouldOmitPatientAge_whenAgeUnavailable() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);
        Demographic noAge = spy(fakePatient());
        when(noAge.getAgeAsOf(any(java.util.Date.class), any(java.util.Locale.class))).thenReturn(null);
        when(demographicManager.getDemographic(any(), eq(2))).thenReturn(noAge);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("csPatientName")).isEqualTo("FAKE-Family, FAKE-Given");
        assertThat(request.getAttribute("csPatientAge")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void shouldOmitPatientAge_whenYearOfBirthBlank(String yearOfBirth) throws Exception {
        assertAgeOmittedForYearOfBirth(yearOfBirth);
    }

    @Test
    void shouldOmitPatientAge_whenYearOfBirthNull() throws Exception {
        assertAgeOmittedForYearOfBirth(null);
    }

    // Demographic.getAgeAsOf substitutes 1900 for an empty year, which would show a fabricated age.
    private void assertAgeOmittedForYearOfBirth(String yearOfBirth) throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);
        Demographic noYear = fakePatient();
        noYear.setYearOfBirth(yearOfBirth);
        when(demographicManager.getDemographic(any(), eq(2))).thenReturn(noYear);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("csPatientName")).isEqualTo("FAKE-Family, FAKE-Given");
        assertThat(request.getAttribute("csPatientAge")).isNull();
    }

    @Test
    void shouldReturn404_whenPatientDoesNotExist() throws Exception {
        request.setParameter("demographicNo", "2");
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);
        when(demographicManager.getDemographic(any(), eq(2))).thenReturn(null);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("csPatientName")).isNull();
    }

    @Test
    void shouldNotLookUpPatient_whenValidatorDenies() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
        verify(demographicManager, never()).getDemographic(any(), any(Integer.class));
    }

    @Test
    void shouldNotLookUpPatient_whenMethodRejected() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", "2");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(demographicManager);
    }
}
