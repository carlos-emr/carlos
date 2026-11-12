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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * The BC invoice view hands the invoice it loaded to its own page only (issue #4673). It used to
 * leave it in the session as {@code billingViewBean}, where the receipt shown after "Update
 * Invoice" read it: with a second invoice opened in another window in the meantime, that receipt
 * showed and printed the other patient's invoice in place of the one just updated.
 *
 * @since 2026-10-16
 */
@DisplayName("BillingView2Action")
@Tag("unit")
@Tag("billing")
class BillingView2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    private final MockHttpSession session = new MockHttpSession();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldHandEachWindowItsOwnInvoice_whenTwoInvoicesAreViewedInOneLogin() throws Exception {
        try (MockedConstruction<BillingViewBean> beans = mockConstruction(BillingViewBean.class,
                     (bean, context) -> {
                         // Each view loads the invoice it names; patient = billing number + 1000.
                         String[] loaded = new String[1];
                         doAnswer(call -> {
                             loaded[0] = call.getArgument(0);
                             return null;
                         }).when(bean).loadBilling(anyString());
                         when(bean.getBillingNo()).thenAnswer(call -> loaded[0]);
                         when(bean.getPatientNo()).thenAnswer(call ->
                                 String.valueOf(Integer.parseInt(loaded[0]) + 1000));
                         when(bean.getBillRecipient(anyString())).thenReturn(List.of());
                     });
             MockedConstruction<BillingBillingManager> managers = mockConstruction(BillingBillingManager.class,
                     (manager, context) -> {
                         when(manager.getBillView(anyString())).thenReturn(new ArrayList<>());
                         when(manager.getGrandTotal(any())).thenReturn("0.00");
                     });
             MockedConstruction<DemographicData> demographics = mockConstruction(DemographicData.class,
                     (data, context) -> when(data.getDemographic(any(), anyString()))
                             .thenAnswer(call -> patient(call.getArgument(1))))) {

            String first = view("101");
            BillingViewBean firstInvoice = (BillingViewBean) request.getAttribute("billingViewBean");
            String second = view("202");
            BillingViewBean secondInvoice = (BillingViewBean) request.getAttribute("billingViewBean");

            assertThat(first).isEqualTo(ActionSupport.SUCCESS);
            assertThat(second).isEqualTo(ActionSupport.SUCCESS);
            assertThat(firstInvoice.getBillingNo()).isEqualTo("101");
            assertThat(secondInvoice.getBillingNo()).isEqualTo("202");
            assertThat(session.getAttribute("billingViewBean")).isNull();
        }
    }

    /**
     * A private bill is saved one row per service line and its receipt names them all. The page
     * gets every one of them, so "Update Invoice" can show the whole receipt again.
     */
    @Test
    void shouldHandThePageEveryBillOfTheReceipt_whenItCombinesSeveralBills() throws Exception {
        try (MockedConstruction<BillingViewBean> beans = mockConstruction(BillingViewBean.class,
                     (bean, context) -> {
                         when(bean.getBillingNo()).thenReturn("101");
                         when(bean.getPatientNo()).thenReturn("1101");
                         when(bean.getBillRecipient(anyString())).thenReturn(List.of());
                     });
             MockedConstruction<BillingBillingManager> managers = mockConstruction(BillingBillingManager.class,
                     (manager, context) -> {
                         when(manager.getBillView(anyString())).thenReturn(new ArrayList<>());
                         when(manager.getGrandTotal(any())).thenReturn("0.00");
                     });
             MockedConstruction<DemographicData> demographics = mockConstruction(DemographicData.class,
                     (data, context) -> when(data.getDemographic(any(), anyString()))
                             .thenAnswer(call -> patient(call.getArgument(1))))) {

            String result = view("101", "102");

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute(BillingView2Action.RECEIPT_BILLING_NOS)).isEqualTo(List.of("101", "102"));
            verify(managers.constructed().get(0)).getBillView("101");
            verify(managers.constructed().get(0)).getBillView("102");
        }
    }

    private String view(String... billingNos) throws Exception {
        request = new MockHttpServletRequest();
        request.setSession(session);
        for (String billingNo : billingNos) {
            request.addParameter("billing_no", billingNo);
        }
        response = new MockHttpServletResponse();
        return new BillingView2Action().execute();
    }

    private static Demographic patient(String demographicNo) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(Integer.valueOf(demographicNo));
        demographic.setFirstName("Test");
        demographic.setLastName("Patient" + demographicNo);
        demographic.setHin("");
        demographic.setVer("");
        return demographic;
    }
}
