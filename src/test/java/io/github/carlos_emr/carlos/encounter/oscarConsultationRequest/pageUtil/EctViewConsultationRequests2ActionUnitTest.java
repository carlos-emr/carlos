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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Consultant / Provider (MRP) filter resolution in
 * {@link EctViewConsultationRequests2Action} (issue #3976).
 *
 * @since 2026-09-30
 */
@DisplayName("EctViewConsultationRequests2Action consultant and provider filters")
@Tag("unit")
@Tag("consultation")
class EctViewConsultationRequests2ActionUnitTest extends CarlosUnitTestBase {

    private static final ConsultationMrpOptionDto MRP_IN_SCOPE = new ConsultationMrpOptionDto("101", "Lovelace", "Ada");
    private static final ConsultationMrpOptionDto MRP_OUT_OF_SCOPE = new ConsultationMrpOptionDto("202", "Hopper", "Grace");

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private ConsultationRequestDao consultationRequestDao;
    private ProfessionalSpecialistDao professionalSpecialistDao;
    private ConsultationListProviderScopeResolver scopeResolver;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);
        professionalSpecialistDao = mock(ProfessionalSpecialistDao.class);
        scopeResolver = mock(ConsultationListProviderScopeResolver.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/encounter/ViewConsultation");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull()))
                .thenReturn(true);
        when(consultationRequestDao.findDistinctConsultMrps()).thenReturn(List.of(MRP_IN_SCOPE, MRP_OUT_OF_SCOPE));
        when(scopeResolver.resolveAllowedProviderNos(loggedInInfo)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private ConsultationListFilterResolver filterResolver() {
        return new ConsultationListFilterResolver(consultationRequestDao, professionalSpecialistDao, scopeResolver);
    }

    private EctViewConsultationRequests2Action newAction() {
        return new EctViewConsultationRequests2Action(securityInfoManager, filterResolver());
    }

    @Test
    @DisplayName("should reject a user without _con read before resolving any filter")
    void shouldThrowSecurityException_whenConPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        EctViewConsultationRequests2Action action = newAction();
        action.setConsultantId("5");
        action.setFilterProviderNo("101");

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
        verifyNoInteractions(consultationRequestDao, professionalSpecialistDao, scopeResolver);
    }

    @Test
    @DisplayName("should publish the consultant id and label when the specialist exists")
    void shouldPublishConsultant_whenSpecialistExists() throws Exception {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setLastName("Smith");
        specialist.setFirstName("Brian");
        when(professionalSpecialistDao.find(Integer.valueOf(5))).thenReturn(specialist);
        EctViewConsultationRequests2Action action = newAction();
        action.setConsultantId(" 5 ");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("consultListConsultantId")).isEqualTo(5);
        assertThat(request.getAttribute("consultListConsultantLabel")).isEqualTo("Smith, Brian");
    }

    @Test
    @DisplayName("should ignore a consultant id that names no specialist")
    void shouldIgnoreConsultant_whenSpecialistUnknown() throws Exception {
        when(professionalSpecialistDao.find(Integer.valueOf(9999))).thenReturn(null);
        EctViewConsultationRequests2Action action = newAction();
        action.setConsultantId("9999");

        action.execute();

        assertThat(request.getAttribute("consultListConsultantId")).isNull();
        assertThat(request.getAttribute("consultListConsultantLabel")).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "  ", "abc", "-3", "0", "1 OR 1=1", "99999999999"})
    @DisplayName("should ignore a malformed consultant id without looking it up")
    void shouldIgnoreConsultant_whenIdMalformed(String consultantId) throws Exception {
        EctViewConsultationRequests2Action action = newAction();
        action.setConsultantId(consultantId);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("consultListConsultantId")).isNull();
        verify(professionalSpecialistDao, never()).find(any());
    }

    @Test
    @DisplayName("should apply any provider and list every MRP when no access privacy applies")
    void shouldApplyProviderFilter_whenUnrestricted() throws Exception {
        EctViewConsultationRequests2Action action = newAction();
        action.setFilterProviderNo("202");

        action.execute();

        assertThat(request.getAttribute("consultListFilterProviderNo")).isEqualTo("202");
        assertThat(request.getAttribute("consultListMrpOptions")).isEqualTo(List.of(MRP_IN_SCOPE, MRP_OUT_OF_SCOPE));
    }

    @Test
    @DisplayName("should ignore an out-of-scope provider and hide it from the dropdown under access privacy")
    void shouldIgnoreOutOfScopeProvider_whenAccessPrivacyApplies() throws Exception {
        when(scopeResolver.resolveAllowedProviderNos(loggedInInfo)).thenReturn(Optional.of(Set.of("101")));
        EctViewConsultationRequests2Action action = newAction();
        action.setFilterProviderNo("202");

        action.execute();

        assertThat(request.getAttribute("consultListFilterProviderNo")).isNull();
        assertThat(request.getAttribute("consultListMrpOptions")).isEqualTo(List.of(MRP_IN_SCOPE));
    }

    @Test
    @DisplayName("should apply an in-scope provider under access privacy")
    void shouldApplyInScopeProvider_whenAccessPrivacyApplies() throws Exception {
        when(scopeResolver.resolveAllowedProviderNos(loggedInInfo)).thenReturn(Optional.of(Set.of("101")));
        EctViewConsultationRequests2Action action = newAction();
        action.setFilterProviderNo("101");

        action.execute();

        assertThat(request.getAttribute("consultListFilterProviderNo")).isEqualTo("101");
    }

    @Test
    @DisplayName("should populate the provider dropdown when the list is opened from the schedule banner")
    void shouldPublishMrpOptions_whenOpenedThroughIncomingConsultation() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);
        when(scopeResolver.resolveAllowedProviderNos(loggedInInfo)).thenReturn(Optional.of(Set.of("202")));

        String result = new EctIncomingConsultation2Action(securityInfoManager, filterResolver()).execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("consultListMrpOptions")).isEqualTo(List.of(MRP_OUT_OF_SCOPE));
        assertThat(request.getAttribute("consultListFilterProviderNo")).isNull();
        assertThat(request.getAttribute("consultListConsultantId")).isNull();
    }

    @Test
    @DisplayName("should reject the schedule banner entry without _con write before querying")
    void shouldThrowSecurityException_whenIncomingConsultationLacksConWrite() {
        assertThatThrownBy(() -> new EctIncomingConsultation2Action(securityInfoManager, filterResolver()).execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
        verifyNoInteractions(consultationRequestDao, scopeResolver);
    }

    @Test
    @DisplayName("should ignore a provider that is not the MRP of any patient with consults")
    void shouldIgnoreProvider_whenNotAmongDropdownOptions() throws Exception {
        EctViewConsultationRequests2Action action = newAction();
        action.setFilterProviderNo("999999");

        action.execute();

        assertThat(request.getAttribute("consultListFilterProviderNo")).isNull();
    }

    @Test
    @DisplayName("should expose no getter that would leak raw filter parameters to the JSP")
    void shouldNotExposeRawFilterGetters_forValueStackFallback() {
        // StrutsRequestWrapper.getAttribute falls back to the value stack, so a getter named after
        // a parameter would hand the JSP the unvalidated value.
        for (java.lang.reflect.Method method : EctViewConsultationRequests2Action.class.getMethods()) {
            assertThat(method.getName()).isNotIn("getConsultantId", "getFilterProviderNo",
                    "getConsultListConsultantId", "getConsultListConsultantLabel",
                    "getConsultListFilterProviderNo", "getConsultListMrpOptions");
        }
    }

    @Test
    @DisplayName("should leave the provider filter unset when none was submitted")
    void shouldLeaveProviderUnset_whenNotSubmitted() throws Exception {
        newAction().execute();

        assertThat(request.getAttribute("consultListFilterProviderNo")).isNull();
        assertThat(request.getAttribute("consultListConsultantId")).isNull();
        assertThat(request.getAttribute("consultListMrpOptions")).isEqualTo(List.of(MRP_IN_SCOPE, MRP_OUT_OF_SCOPE));
    }
}
