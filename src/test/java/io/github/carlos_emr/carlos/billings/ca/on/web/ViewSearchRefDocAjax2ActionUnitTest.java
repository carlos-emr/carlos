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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewSearchRefDocAjax2Action} — the referring-doctor
 * autocomplete JSON endpoint that replaced {@code searchRefDocAjax.jsp}.
 *
 * @since 2026-04-26
 */
@DisplayName("ViewSearchRefDocAjax2Action")
@Tag("unit")
@Tag("billing")
class ViewSearchRefDocAjax2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private ProfessionalSpecialistDao mockProfessionalSpecialistDao;

    @Mock
    private ConsultationServiceDao mockConsultationServiceDao;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        // Default: empty results
        when(mockProfessionalSpecialistDao.findByFullName(any(), any())).thenReturn(Collections.emptyList());
        when(mockProfessionalSpecialistDao.findByReferralNo(any())).thenReturn(Collections.emptyList());
        when(mockProfessionalSpecialistDao.findByFullNameAndSpecialtyAndAddress(
                any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(mockProfessionalSpecialistDao.findByPhoneContains(any(), anyInt())).thenReturn(Collections.emptyList());
        when(mockConsultationServiceDao.findAll()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private ViewSearchRefDocAjax2Action newAction() {
        return new ViewSearchRefDocAjax2Action(
                mockSecurityInfoManager, mockProfessionalSpecialistDao, mockConsultationServiceDao);
    }

    private static ProfessionalSpecialist makePs(String refNo, String last, String first, String spec) {
        ProfessionalSpecialist ps = new ProfessionalSpecialist();
        ps.setReferralNo(refNo);
        ps.setLastName(last);
        ps.setFirstName(first);
        ps.setSpecialtyType(spec);
        ps.setStreetAddress("100 King St");
        ps.setPhoneNumber("416-555-0100");
        return ps;
    }

    @Test
    void shouldReturnEmptyJsonArray_whenTermShorterThan2Chars() throws Exception {
        mockRequest.setParameter("term", "a");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).isEqualTo("[]");
        assertThat(mockResponse.getContentType()).contains("application/json");
        verify(mockProfessionalSpecialistDao, never()).findByFullName(any(), any());
    }

    @Test
    void shouldMergeAcrossSearchModes_andDedupByReferralNo() throws Exception {
        ProfessionalSpecialist a = makePs("1001", "Smith", "John", "8");
        ProfessionalSpecialist b = makePs("1002", "Smithers", "Jane", "9");
        ProfessionalSpecialist aDup = makePs("1001", "Smith", "John", "8");

        when(mockProfessionalSpecialistDao.findByFullName("Smit", ""))
                .thenReturn(Arrays.asList(a, b));
        when(mockProfessionalSpecialistDao.findByReferralNo("Smit%"))
                .thenReturn(Collections.singletonList(aDup));

        mockRequest.setParameter("term", "Smit");

        newAction().execute();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(mockResponse.getContentAsString());
        assertThat(array.isArray()).isTrue();
        assertThat(array.size()).isEqualTo(2);
        assertThat(array.get(0).get("referralNo").asText()).isEqualTo("1001");
        assertThat(array.get(1).get("referralNo").asText()).isEqualTo("1002");
    }

    @Test
    void shouldResolveSpecialtyTypeViaConsultationServiceDao() throws Exception {
        ProfessionalSpecialist ps = makePs("1001", "Smith", "John", "8");
        ConsultationServices svc = new ConsultationServices();
        svc.setServiceId(8);
        svc.setServiceDesc("Cardiology");

        when(mockProfessionalSpecialistDao.findByFullName("Smit", ""))
                .thenReturn(Collections.singletonList(ps));
        when(mockConsultationServiceDao.findAll())
                .thenReturn(Collections.singletonList(svc));

        mockRequest.setParameter("term", "Smit");

        newAction().execute();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(mockResponse.getContentAsString());
        assertThat(array.get(0).get("specialtyType").asText()).isEqualTo("Cardiology");
    }

    @Test
    void shouldFallBackToSpecialtyCode_whenServiceDescNotFound() throws Exception {
        ProfessionalSpecialist ps = makePs("1001", "Smith", "John", "99");
        when(mockProfessionalSpecialistDao.findByFullName("Smit", ""))
                .thenReturn(Collections.singletonList(ps));

        mockRequest.setParameter("term", "Smit");

        newAction().execute();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(mockResponse.getContentAsString());
        assertThat(array.get(0).get("specialtyType").asText()).isEqualTo("99");
    }

    @Test
    void shouldLimitResults_to20() throws Exception {
        java.util.List<ProfessionalSpecialist> twentyFive = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            twentyFive.add(makePs(String.valueOf(2000 + i), "Smith" + i, "X", "8"));
        }
        when(mockProfessionalSpecialistDao.findByFullName("Smit", "")).thenReturn(twentyFive);

        mockRequest.setParameter("term", "Smit");

        newAction().execute();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode array = mapper.readTree(mockResponse.getContentAsString());
        assertThat(array.size()).isEqualTo(20);
    }

    @Test
    void shouldReturn401_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(mockProfessionalSpecialistDao, never()).findByFullName(any(), any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);
        mockRequest.setParameter("term", "Smit");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockProfessionalSpecialistDao, never()).findByFullName(any(), any());
    }

    @Test
    void shouldCacheSpecialtyMap_acrossInvocations() {
        ConsultationServices svc = new ConsultationServices();
        svc.setServiceId(8);
        svc.setServiceDesc("Cardiology");
        when(mockConsultationServiceDao.findAll()).thenReturn(Collections.singletonList(svc));

        mockRequest.setParameter("term", "Smit");

        newAction().execute();
        newAction().execute();
        newAction().execute();

        // findAll() should have been called only once — second/third hit the cache
        verify(mockConsultationServiceDao, org.mockito.Mockito.times(1)).findAll();
    }
}
