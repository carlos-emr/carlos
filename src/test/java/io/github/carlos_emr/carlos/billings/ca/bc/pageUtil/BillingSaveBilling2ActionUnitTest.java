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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void shouldIncludeContextPath_whenBuildingReceiptRedirect() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl(
                "/carlos", List.of("101", "102"));

        assertThat(redirectUrl)
                .isEqualTo("/carlos/billing/CA/BC/billingView?billing_no=101&billing_no=102&receipt=yes");
    }

    @Test
    void shouldUseRootRelativeBillingRoute_whenContextPathIsEmpty() {
        String redirectUrl = BillingSaveBilling2Action.receiptRedirectUrl("", List.of("101"));

        assertThat(redirectUrl)
                .isEqualTo("/billing/CA/BC/billingView?billing_no=101&receipt=yes");
    }

    @Test
    void shouldRejectGetBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("GET");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }

    @Test
    void shouldRejectHeadBeforeBillingWrites_whenSavingBilling() throws Exception {
        request.setMethod("HEAD");

        String result = new BillingSaveBilling2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, billingmasterDAO, appointmentDao, appointmentArchiveDao);
    }
}
