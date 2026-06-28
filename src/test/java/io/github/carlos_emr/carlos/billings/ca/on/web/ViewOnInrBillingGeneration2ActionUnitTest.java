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

import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimPersister;
import io.github.carlos_emr.carlos.commn.model.Demographic;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for {@link ViewOnInrBillingGeneration2Action} — the INR claim
 * generator extracted from the legacy
 * {@code billing/CA/ON/inr/onGenINRbilling.jsp}.
 *
 * <p>Covers the gate contract (null-session, missing-privilege, 405 on
 * non-POST), the per-{@code inrbilling<id>}-param iteration, and
 * delegation to {@link BillingOnClaimPersister} with a
 * correctly-shaped {@link BillingClaimHeaderDto}.</p>
 *
 * @since 2026-04-26
 */
@DisplayName("ViewOnInrBillingGeneration2Action")
@Tag("unit")
@Tag("billing")
class ViewOnInrBillingGeneration2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingInrDao mockBillingInrDao;

    @Mock
    private BillingOnClaimPersister mockPersistenceService;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private ViewOnInrBillingGeneration2Action newAction() {
        return new ViewOnInrBillingGeneration2Action(
                mockSecurityInfoManager, mockBillingInrDao, mockPersistenceService);
    }

    private static BillingInr makeInr() {
        BillingInr inr = new BillingInr();
        inr.setId(7);
        inr.setDemographicNo(101);
        inr.setDemographicName("Doe, Jane");
        inr.setProviderNo("999998");
        inr.setProviderOhipNo("OHIP1");
        inr.setProviderRmaNo("RMA1");
        inr.setDiagnosticCode("401");
        inr.setServiceCode("A007");
        inr.setServiceDesc("Office visit");
        inr.setBillingAmount("33.70");
        inr.setBillingUnit("1");
        inr.setStatus("O");
        return inr;
    }

    private static Demographic makeDemo() {
        Demographic demo = new Demographic();
        demo.setDemographicNo(101);
        demo.setHin("9876543225");
        demo.setVer("AB");
        demo.setHcType("ON");
        demo.setSex("F");
        demo.setYearOfBirth("1985");
        demo.setMonthOfBirth("06");
        demo.setDateOfBirth("15");
        return demo;
    }

    @Test
    void shouldPersistClaim_perInrBillingParam() {
        BillingInr inr = makeInr();
        Demographic demo = makeDemo();
        when(mockBillingInrDao.search_inrbilling_dt_billno(7))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{inr, demo}));
        when(mockBillingInrDao.find(7)).thenReturn(inr);
        when(mockPersistenceService.addOneClaimHeaderRecord(any(BillingClaimHeaderDto.class)))
                .thenReturn(555);

        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("clinic_no", "C1");
        mockRequest.setParameter("xml_location", "REF1");
        mockRequest.setParameter("curUser", "spoofed");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<BillingClaimHeaderDto> headerCaptor =
                ArgumentCaptor.forClass(BillingClaimHeaderDto.class);
        verify(mockPersistenceService).addOneClaimHeaderRecord(headerCaptor.capture());
        BillingClaimHeaderDto h = headerCaptor.getValue();
        assertThat(h.demographicNo()).isEqualTo("101");
        assertThat(h.getProviderNo()).isEqualTo("999998");
        assertThat(h.providerOhipNo()).isEqualTo("OHIP1");
        assertThat(h.payProgram()).isEqualTo("HCP"); // hcType=ON
        assertThat(h.getProvince()).isEqualTo("ON");
        assertThat(h.billingDate()).isEqualTo("2026-04-26");
        assertThat(h.getTotal()).isEqualTo("33.70");
        assertThat(h.getCreator()).isEqualTo("999998");
        assertThat(h.getLocation()).isEqualTo("C1");
        assertThat(h.facilityNumber()).isEqualTo("REF1");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<BillingClaimItemDto>> itemsCaptor =
                (ArgumentCaptor<List<BillingClaimItemDto>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(mockPersistenceService).addItemRecord(itemsCaptor.capture(), eq(555));
        List<BillingClaimItemDto> items = itemsCaptor.getValue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).serviceCode()).isEqualTo("A007");
        assertThat(items.get(0).getDx()).isEqualTo("401");
        assertThat(items.get(0).getFee()).isEqualTo("33.70");

        verify(mockBillingInrDao).merge(any(BillingInr.class));
        assertThat(inr.getStatus()).isEqualTo("A");
    }

    @Test
    void shouldUseRMB_whenHcTypeIsNotON() {
        BillingInr inr = makeInr();
        Demographic demo = makeDemo();
        demo.setHcType("BC");
        when(mockBillingInrDao.search_inrbilling_dt_billno(7))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{inr, demo}));
        when(mockBillingInrDao.find(7)).thenReturn(inr);
        when(mockPersistenceService.addOneClaimHeaderRecord(any(BillingClaimHeaderDto.class)))
                .thenReturn(555);

        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        newAction().execute();

        ArgumentCaptor<BillingClaimHeaderDto> headerCaptor =
                ArgumentCaptor.forClass(BillingClaimHeaderDto.class);
        verify(mockPersistenceService).addOneClaimHeaderRecord(headerCaptor.capture());
        assertThat(headerCaptor.getValue().payProgram()).isEqualTo("RMB");
    }

    @Test
    void shouldSkipMerge_whenInrIsDeleted() {
        BillingInr inr = makeInr();
        inr.setStatus("D");
        Demographic demo = makeDemo();
        when(mockBillingInrDao.search_inrbilling_dt_billno(7))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{inr, demo}));
        when(mockBillingInrDao.find(7)).thenReturn(inr);
        when(mockPersistenceService.addOneClaimHeaderRecord(any(BillingClaimHeaderDto.class)))
                .thenReturn(555);

        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        newAction().execute();

        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
        assertThat(inr.getStatus()).isEqualTo("D");
    }

    @Test
    void shouldIgnoreNonInrParams_forIrrelevantInput() {
        mockRequest.setParameter("clinic_no", "C1");
        mockRequest.setParameter("not_inr_at_all", "x");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
        verify(mockBillingInrDao, never()).search_inrbilling_dt_billno(anyInt());
    }

    @Test
    void shouldIgnoreMalformedInrBillingParams_forInvalidParameterNames() {
        mockRequest.setParameter("clinic_no", "C1");
        mockRequest.setParameter("inrbilling", "on");
        mockRequest.setParameter("inrbillingABC", "on");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
        verify(mockBillingInrDao, never()).search_inrbilling_dt_billno(anyInt());
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");

        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldThrowSecurityException_whenProviderNumberMissing(String providerNo) {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(providerNo);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("provider number");

        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
    }

    @Test
    void shouldReturn405_whenNotPost() {
        mockRequest.setMethod("GET");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockPersistenceService, never()).addOneClaimHeaderRecord(any());
    }
}
