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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChartSpaceRequestValidator}. They pin the check order
 * shared by every ChartSpace action: method, session, parameter, privilege,
 * record access, program domain.
 *
 * <p>{@link Isolated}: the tests mutate the process-wide {@code CarlosProperties}
 * singleton, and Surefire runs test classes in parallel.</p>
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Isolated
class ChartSpaceRequestValidatorUnitTest {

    private static final String SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private LoggedInInfo info;
    private SecurityInfoManager securityInfoManager;
    private CaseManagementManager caseManagementManager;
    private ChartSpaceRequestValidator validator;

    private static final String DOMAIN_KEY = "program_domain.show_echart";

    /** Raw original value on the shared singleton (null when undefined), restored after each test. */
    private Object originalDomainValue;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        session = new MockHttpSession();
        request.setSession(session);
        request.setMethod("GET");
        info = mock(LoggedInInfo.class);
        session.setAttribute(SESSION_KEY, info);
        securityInfoManager = mock(SecurityInfoManager.class);
        caseManagementManager = mock(CaseManagementManager.class);
        validator = new ChartSpaceRequestValidator(securityInfoManager, caseManagementManager);
        // Hashtable read: getProperty(String) would log a missing-key warning and apply defaults.
        originalDomainValue = CarlosProperties.getInstance().get(DOMAIN_KEY);
        CarlosProperties.getInstance().remove(DOMAIN_KEY);
    }

    @AfterEach
    void restoreProperty() {
        if (originalDomainValue == null) {
            CarlosProperties.getInstance().remove(DOMAIN_KEY);
        } else {
            CarlosProperties.getInstance().setProperty(DOMAIN_KEY, (String) originalDomainValue);
        }
    }

    /** Grants everything the validator checks, so a test can then withdraw one piece. */
    private void allowAll() {
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(info), anyString(), eq("r"), eq("2"))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(info, 2)).thenReturn(true);
        when(caseManagementManager.isClientInProgramDomain("999998", "2")).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void shouldWrite405_forNonGetMethods(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "2");

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isEmpty();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(securityInfoManager, caseManagementManager);
    }

    @Test
    void shouldThrowSecurityException_whenNoLoggedInInfo() {
        session.removeAttribute(SESSION_KEY);
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing session");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldWrite400_whenDemographicNoInvalid() throws Exception {
        request.setParameter("demographicNo", "abc");

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isEmpty();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    void shouldThrowSecurityException_whenEChartReadMissing() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
    }

    @Test
    void shouldReturnValidated_whenEverythingChecksOut() throws Exception {
        request.setParameter("demographicNo", "2");
        allowAll();

        Optional<ChartSpaceRequestValidator.Validated> result = validator.validate(request, response);

        assertThat(result).isPresent();
        assertThat(result.get().demographicNo()).isEqualTo(2);
        assertThat(result.get().loggedInInfo()).isSameAs(info);
        verify(securityInfoManager).hasPrivilege(info, "_eChart", "r", "2");
    }

    @Test
    void shouldAccept_forHead() throws Exception {
        request.setMethod("HEAD");
        request.setParameter("demographicNo", "2");
        allowAll();

        assertThat(validator.validate(request, response)).isPresent();
    }

    @Test
    void shouldNotTouchSecurity_whenMethodInvalid() throws Exception {
        request.setMethod("POST");
        request.setParameter("demographicNo", "2");

        assertThat(validator.validate(request, response)).isEmpty();

        verifyNoInteractions(securityInfoManager, caseManagementManager);
    }

    @Test
    void shouldThrow_whenDemographicReadMissing() {
        request.setParameter("demographicNo", "2");
        allowAll();
        when(securityInfoManager.hasPrivilege(eq(info), eq("_demographic"), eq("r"), any())).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(caseManagementManager);
    }

    @Test
    void shouldThrow_whenPatientRecordLocked() {
        request.setParameter("demographicNo", "2");
        allowAll();
        when(securityInfoManager.isAllowedAccessToPatientRecord(info, 2)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("patient record access denied");
        verifyNoInteractions(caseManagementManager);
    }

    @Test
    void shouldThrow_whenPatientOutsideProgramDomain() {
        request.setParameter("demographicNo", "2");
        allowAll();
        when(caseManagementManager.isClientInProgramDomain("999998", "2")).thenReturn(false);
        when(caseManagementManager.isClientReferredInProgramDomain("999998", "2")).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessage("patient outside program domain");
    }

    @Test
    void shouldPass_whenPatientInProgramDomain() throws Exception {
        request.setParameter("demographicNo", "2");
        allowAll();
        when(caseManagementManager.isClientInProgramDomain("999998", "2")).thenReturn(true);
        when(caseManagementManager.isClientReferredInProgramDomain("999998", "2")).thenReturn(false);

        assertThat(validator.validate(request, response)).isPresent();
    }

    @Test
    void shouldPass_whenPatientReferredInProgramDomain() throws Exception {
        request.setParameter("demographicNo", "2");
        allowAll();
        when(caseManagementManager.isClientInProgramDomain("999998", "2")).thenReturn(false);
        when(caseManagementManager.isClientReferredInProgramDomain("999998", "2")).thenReturn(true);

        assertThat(validator.validate(request, response)).isPresent();
    }

    @Test
    void shouldSkipDomainCheck_whenShowEChartPropertyTrue() throws Exception {
        request.setParameter("demographicNo", "2");
        allowAll();
        CarlosProperties.getInstance().setProperty(DOMAIN_KEY, "true");

        assertThat(validator.validate(request, response)).isPresent();

        verifyNoInteractions(caseManagementManager);
    }
}
