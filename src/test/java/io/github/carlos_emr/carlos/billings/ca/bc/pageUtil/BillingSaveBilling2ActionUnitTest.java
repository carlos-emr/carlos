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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("BillingSaveBilling2Action")
@Tag("unit")
@Tag("billing")
class BillingSaveBilling2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private AppointmentArchiveDao appointmentArchiveDao;
    @Mock private OscarAppointmentDao appointmentDao;
    @Mock private BillingmasterDAO billingmasterDAO;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(AppointmentArchiveDao.class, appointmentArchiveDao);
        registerMock(OscarAppointmentDao.class, appointmentDao);
        registerMock(BillingmasterDAO.class, billingmasterDAO);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    @DisplayName("should include context path when building receipt redirect")
    void shouldIncludeContextPath_whenBuildingReceiptRedirect() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl(
                "/carlos", List.of("101", "102"));

        assertThat(redirectUrl)
                .isEqualTo("/carlos/billing/CA/BC/billingView?billing_no=101&billing_no=102&receipt=yes");
    }

    @Test
    @DisplayName("should use root-relative billing route when context path is empty")
    void shouldUseRootRelativeBillingRoute_whenContextPathIsEmpty() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl("", List.of("101"));

        assertThat(redirectUrl)
                .isEqualTo("/billing/CA/BC/billingView?billing_no=101&receipt=yes");
    }

    @Test
    @DisplayName("should reject GET before billing writes")
    void shouldRejectGetBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("GET");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should reject HEAD before billing writes")
    void shouldRejectHeadBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("HEAD");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should redirect unauthenticated request when session user attribute is missing")
    void shouldRedirectUnauthenticatedRequest_whenSessionUserAttributeIsMissing() throws Exception {
        request.setMethod("POST");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/logoutPage");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    @DisplayName("should send bad request when billing session bean is missing")
    void shouldSendBadRequest_whenBillingSessionBeanIsMissing() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getErrorMessage()).isEqualTo("Billing session expired");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    @Test
    @DisplayName("should skip appointment update when appointment number is malformed")
    void shouldSkipAppointmentUpdate_whenAppointmentNumberIsMalformed() throws Exception {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        request.getSession().setAttribute("billingSessionBean", minimalBillingSessionBean("not-a-number"));
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(true);

            String result = new BillingSaveBilling2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            BillingSessionBean bean = (BillingSessionBean) request.getSession()
                    .getAttribute("billingSessionBean");
            assertThat(bean.getApptNo()).isEqualTo("0");
            verifyNoInteractions(appointmentDao, appointmentArchiveDao, billingmasterDAO);
        }
    }

    @Test
    @DisplayName("should throw security exception when user lacks billing write privilege")
    void shouldThrowSecurityException_whenUserLacksBillingWritePrivilege() {
        request.setMethod("POST");
        request.getSession().setAttribute("user", "100");
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
            loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("w"), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> new BillingSaveBilling2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_billing)");
            verifyNoInteractions(billingmasterDAO, appointmentDao, appointmentArchiveDao);
        }
    }

    private BillingSessionBean minimalBillingSessionBean(String apptNo) {
        BillingSessionBean bean = new BillingSessionBean();
        bean.setApptNo(apptNo);
        bean.setEncounter("E");
        bean.setBillingType("MSP");
        bean.setBillItem(new java.util.ArrayList<>());
        return bean;
    }
}
