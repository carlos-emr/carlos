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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * BC billing setup opens each bill under its own key and resumes only the bill a request names,
 * so a bill opened in one window is never shown in another (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC billing setup opens and resumes bills by key")
class BillingBCSetup2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpSession session = new MockHttpSession();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<BillingGuidelines> guidelinesMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_billing"), eq("r"), isNull())).thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(invocation -> response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        BillingGuidelines guidelines = mock(BillingGuidelines.class);
        when(guidelines.evaluateAndGetConsequences(any(), anyString(), anyString())).thenReturn(List.of());
        guidelinesMock = mockStatic(BillingGuidelines.class);
        guidelinesMock.when(BillingGuidelines::getInstance).thenReturn(guidelines);
        newRequest();
    }

    @AfterEach
    void tearDown() {
        guidelinesMock.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    void shouldKeepEachWindowsBill_whenTwoPatientsBillsAreOpened() throws Exception {
        String keyA = openBill("101", "11");
        BillingSessionBean billA = (BillingSessionBean) request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE);
        newRequest();
        String keyB = openBill("202", "22");

        assertThat(keyA).isNotEqualTo(keyB);
        assertThat(billA.getPatientNo()).isEqualTo("101");
        // Window A returns to its bill form (a validation failure or Go Back names its key).
        newRequest();
        request.setParameter(BcBillStaging.KEY_PARAMETER, keyA);

        assertThat(new BillingBCSetup2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        BillingSessionBean shown = (BillingSessionBean) request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE);
        assertThat(shown.getPatientNo()).isEqualTo("101");
        assertThat(shown.getApptNo()).isEqualTo("11");
        assertThat(request.getAttribute(BcBillStaging.KEY_ATTRIBUTE)).isEqualTo(keyA);
        // Nothing login-wide for a later request to fall back to.
        assertThat(session.getAttribute("billingSessionBean")).isNull();
    }

    @Test
    void shouldRefuseTheForm_whenTheRequestNamesNoOpenBill() throws Exception {
        openBill("202", "22");
        newRequest();

        assertThat(new BillingBCSetup2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        // Told why, as the save step tells it, rather than a bare status.
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("Billing session expired.");
        assertThat(request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldKeepTheWindowsBill_whenTheFormReRendersInPlaceRepeatedly() throws Exception {
        String key = openBill("101", "11");
        // Changing the provider, bill type or form reloads the form's patient bar and code table in
        // place (a jQuery load of the same page, an XMLHttpRequest), more often than bills are kept.
        for (int i = 0; i <= BcBillStaging.MAX_ENTRIES; i++) {
            newRequest();
            request.addHeader("X-Requested-With", "XMLHttpRequest");
            assertThat(openBill("101", "11")).isNull();
            assertThat(((BillingSessionBean) request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE)).getPatientNo())
                    .isEqualTo("101");
        }

        // Continue, a validation failure or Go Back still finds the bill the form opened with.
        newRequest();
        request.setParameter(BcBillStaging.KEY_PARAMETER, key);

        assertThat(new BillingBCSetup2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(BcBillStaging.KEY_ATTRIBUTE)).isEqualTo(key);
    }

    @Test
    void shouldResumeTheForwardedBill_whenTheEncounterSaveAndBillForwardsHere() throws Exception {
        BillingSessionBean fromEncounter = new BillingSessionBean();
        fromEncounter.setPatientNo("303");
        BcBillStaging.open(request, fromEncounter);

        // The forward keeps this request: no parameters, the key in its attributes.
        assertThat(new BillingBCSetup2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE)).isSameAs(fromEncounter);
        assertThat(session.getAttribute("billingSessionBean")).isNull();
    }

    private String openBill(String demographicNo, String appointmentNo) throws Exception {
        request.setParameter("demographic_no", demographicNo);
        request.setParameter("appointment_no", appointmentNo);
        assertThat(new BillingBCSetup2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        return (String) request.getAttribute(BcBillStaging.KEY_ATTRIBUTE);
    }

    private void newRequest() {
        request = new MockHttpServletRequest("GET", "/carlos/billing");
        request.setSession(session);
        response = new MockHttpServletResponse();
    }
}
