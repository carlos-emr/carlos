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

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

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
 * Unit tests for {@link ViewInrBillingGeneration2Action} — the legacy non-OHIP
 * INR billing generator extracted from {@code inr/genINRbilling.jsp}.
 *
 * @since 2026-04-26
 */
@DisplayName("ViewInrBillingGeneration2Action")
@Tag("unit")
@Tag("billing")
class ViewInrBillingGeneration2ActionUnitTest extends CarlosUnitTestBase {

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
    private BillingDao mockBillingDao;

    @Mock
    private BillingDetailDao mockBillingDetailDao;

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

    private ViewInrBillingGeneration2Action newAction() {
        return new ViewInrBillingGeneration2Action(
                mockSecurityInfoManager, mockBillingInrDao, mockBillingDao, mockBillingDetailDao);
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
    void shouldPersistBillingAndDetail_perInrBillingParam() throws Exception {
        BillingInr inr = makeInr();
        Demographic demo = makeDemo();
        when(mockBillingInrDao.search_inrbilling_dt_billno(7))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{inr, demo}));
        when(mockBillingInrDao.find(7)).thenReturn(inr);
        when(mockBillingDao.search_billing_no_by_appt(101, 0)).thenReturn(9999);

        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("clinic_no", "12");
        mockRequest.setParameter("xml_location", "REF1");
        mockRequest.setParameter("curUser", "spoofed");
        mockRequest.setParameter("curDate", "2026-04-26");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).contains("self.close()");
        assertThat(mockResponse.getContentType()).contains("text/html");

        ArgumentCaptor<Billing> billingCap = ArgumentCaptor.forClass(Billing.class);
        verify(mockBillingDao).persist(billingCap.capture());
        Billing b = billingCap.getValue();
        assertThat(b.getDemographicNo()).isEqualTo(101);
        assertThat(b.getProviderNo()).isEqualTo("999998");
        assertThat(b.getClinicNo()).isEqualTo(12);
        assertThat(b.getCreator()).isEqualTo("999998");
        assertThat(b.getTotal()).isEqualTo("3370"); // decimal stripped
        assertThat(b.getStatus()).isEqualTo("O");

        ArgumentCaptor<BillingDetail> detailCap = ArgumentCaptor.forClass(BillingDetail.class);
        verify(mockBillingDetailDao).persist(detailCap.capture());
        BillingDetail d = detailCap.getValue();
        assertThat(d.getBillingNo()).isEqualTo(9999);
        assertThat(d.getServiceCode()).isEqualTo("A007");
        assertThat(d.getDiagnosticCode()).isEqualTo("401");
        assertThat(d.getBillingAmount()).isEqualTo("3370");

        verify(mockBillingInrDao).merge(any(BillingInr.class));
        assertThat(inr.getStatus()).isEqualTo("A");
    }

    @Test
    void shouldSkipMerge_whenInrIsDeleted() throws Exception {
        BillingInr inr = makeInr();
        inr.setStatus("D");
        Demographic demo = makeDemo();
        when(mockBillingInrDao.search_inrbilling_dt_billno(7))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{inr, demo}));
        when(mockBillingInrDao.find(7)).thenReturn(inr);
        when(mockBillingDao.search_billing_no_by_appt(anyInt(), anyInt())).thenReturn(9999);

        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("clinic_no", "12");
        mockRequest.setParameter("curDate", "2026-04-26");
        mockRequest.setParameter("xml_appointment_date", "2026-04-26");

        newAction().execute();

        verify(mockBillingInrDao, never()).merge(any(BillingInr.class));
        assertThat(inr.getStatus()).isEqualTo("D");
    }

    @Test
    void shouldIgnoreNonInrParams_forIrrelevantInput() throws Exception {
        mockRequest.setParameter("clinic_no", "12");
        mockRequest.setParameter("not_inr_at_all", "x");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockBillingDao, never()).persist(any());
        verify(mockBillingInrDao, never()).search_inrbilling_dt_billno(anyInt());
    }

    @Test
    void shouldIgnoreMalformedInrBillingParams_forInvalidParameterNames() throws Exception {
        mockRequest.setParameter("inrbilling", "on");
        mockRequest.setParameter("inrbillingABC", "on");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockBillingDao, never()).persist(any());
        verify(mockBillingInrDao, never()).search_inrbilling_dt_billno(anyInt());
    }

    @Test
    void shouldThrowBillingValidationException_whenClinicNumberInvalid() {
        mockRequest.setParameter("inrbilling7", "on");
        mockRequest.setParameter("clinic_no", "not-a-number");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("clinic_no");

        verify(mockBillingDao, never()).persist(any());
        verify(mockBillingInrDao, never()).search_inrbilling_dt_billno(anyInt());
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");

        verify(mockBillingDao, never()).persist(any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockBillingDao, never()).persist(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldThrowSecurityException_whenProviderNumberMissing(String providerNo) {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(providerNo);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("provider number");

        verify(mockBillingDao, never()).persist(any());
    }

    @Test
    void shouldReturn405_whenNotPost() throws Exception {
        mockRequest.setMethod("GET");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockBillingDao, never()).persist(any());
    }
}
