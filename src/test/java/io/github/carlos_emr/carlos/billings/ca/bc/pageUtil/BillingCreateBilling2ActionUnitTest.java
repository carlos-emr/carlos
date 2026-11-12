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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingBillingManager.BillingItem;
import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.SqlUtils;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The BC bill form's Continue (billing/CA/BC/CreateBilling): its warnings read reference tables
 * that a Flyway-built BC install ships empty, and {@link SqlUtils#getQueryResultsList(String)}
 * answers {@code null} for no rows. Continue answered HTTP 500 for every bill (issue #4716).
 * Continue also continues only the bill its form names and keeps the billing form the window
 * shows (issue #4673), and bills a WCB form only when it is the bill's own patient's. A refused
 * Continue keeps its reasons with the bill for the form it returns to (issue #4864 item 10, finding
 * 432); they were request attributes, which its redirect dropped.
 */
@DisplayName("BillingCreateBilling2Action")
@Tag("unit")
@Tag("billing")
class BillingCreateBilling2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<SqlUtils> sqlUtilsMock;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(GstSettingsService.class, mock(GstSettingsService.class));
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        sqlUtilsMock = mockStatic(SqlUtils.class);
        // billing_msp_servicecode_times with no rows
        sqlUtilsMock.when(() -> SqlUtils.getQueryResultsList(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (sqlUtilsMock != null) sqlUtilsMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldAddNoTimeWarning_whenNoServiceCodeNeedsATime() throws Exception {
        BillingCreateBilling2Action action = new BillingCreateBilling2Action();
        ArrayList<BillingItem> items = new ArrayList<>();
        items.add(new BillingBillingManager().new BillingItem("00100", "1"));
        List<String> errors = new ArrayList<>();
        Method validate = BillingCreateBilling2Action.class
                .getDeclaredMethod("validateServiceCodeTimes", ArrayList.class, List.class);
        validate.setAccessible(true);

        assertThatCode(() -> validate.invoke(action, items, errors)).doesNotThrowAnyException();
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldTellTheWindowItsBillingSessionExpired_whenItsBillIsNoLongerOpen() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        request.setSession(new MockHttpSession());
        // A well-formed key whose bill was dropped for newer ones (or never opened).
        request.setParameter(BcBillStaging.KEY_PARAMETER, "AAAAAAAAAAAAAAAAAAAAAA");

        assertThat(new BillingCreateBilling2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("Billing session expired.");
    }

    @Test
    void shouldBillTheWcbForm_whenItIsThePatientsOwn() {
        BillingmasterDAO dao = mock(BillingmasterDAO.class);
        WCB own = new WCB();
        own.setDemographic_no(101);
        when(dao.getWCBForm("7")).thenReturn(own);

        assertThat(BillingCreateBilling2Action.patientsWcbForm(dao, "7", 101)).isSameAs(own);
    }

    @Test
    void shouldRefuseTheWcbForm_whenItIsAnotherPatientsOrMissing() {
        BillingmasterDAO dao = mock(BillingmasterDAO.class);
        WCB other = new WCB();
        other.setDemographic_no(202);
        when(dao.getWCBForm("7")).thenReturn(other);

        assertThat(BillingCreateBilling2Action.patientsWcbForm(dao, "7", 101)).isNull();
        assertThat(BillingCreateBilling2Action.patientsWcbForm(dao, "8", 101)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-7", "7x", "12345678901"})
    void shouldRefuseTheWcbForm_whenItsIdIsMalformed(String wcbId) {
        BillingmasterDAO dao = mock(BillingmasterDAO.class);

        assertThat(BillingCreateBilling2Action.patientsWcbForm(dao, wcbId, 101)).isNull();
        verify(dao, never()).getWCBForm(any());
    }

    @Test
    @DisplayName("should keep a refused Continue's reasons with its bill for the form it returns to")
    void shouldHoldTheRefusalReasons_whenContinueIsRefused() throws Exception {
        request.setContextPath("/carlos");
        request.setSession(new MockHttpSession());
        BillingSessionBean bill = new BillingSessionBean();
        String key = BcBillStaging.open(request, bill);
        BillingCreateBilling2Action action = new BillingCreateBilling2Action();
        action.addActionError("Diagnostic code: 9999 doesn't exist in database<br/>");
        action.addActionError("Service code: FAKE1 requires a start time<br />");

        assertThat(action.refuse(bill, List.of("oscar.billing.CA.BC.billingBC.wcb.error.enterfee")))
                .isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing?billKey=" + key);
        MockHttpServletRequest formRender = new MockHttpServletRequest("GET", "/carlos/billing");
        formRender.setSession(request.getSession());
        formRender.setParameter(BcBillStaging.KEY_PARAMETER, key);
        BillingSessionBean staged = BcBillStaging.resume(formRender);
        // Each message is listed on its own, so the break that ended each one is dropped.
        assertThat(staged.takeRefusalMessages()).containsExactly(
                "Diagnostic code: 9999 doesn't exist in database", "Service code: FAKE1 requires a start time");
        assertThat(staged.takeWcbFormNeeds()).containsExactly("oscar.billing.CA.BC.billingBC.wcb.error.enterfee");
    }

    @Test
    void shouldRecordTheBillingFormTheWindowShows_whenContinuing() {
        BillingCreateBilling2Action action = new BillingCreateBilling2Action();
        BillingSessionBean bill = new BillingSessionBean();
        bill.setBillForm("GP");
        BillingFormData forms = mock(BillingFormData.class);
        when(forms.serviceExists("SPEC")).thenReturn(true);
        action.setBillForm("SPEC");

        action.keepChosenBillForm(bill, forms);

        assertThat(bill.getBillForm()).isEqualTo("SPEC");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "GP' OR '1'='1", "AAAAAAAAAAAAAAAAAAAAAAAAA"})
    void shouldKeepTheBillsForm_whenThePostedFormIsNotAKnownForm(String posted) {
        BillingCreateBilling2Action action = new BillingCreateBilling2Action();
        BillingSessionBean bill = new BillingSessionBean();
        bill.setBillForm("GP");
        BillingFormData forms = mock(BillingFormData.class);
        action.setBillForm(posted);

        action.keepChosenBillForm(bill, forms);

        assertThat(bill.getBillForm()).isEqualTo("GP");
        if (!"UNKNOWN".equals(posted)) {
            // Malformed values never reach the lookup.
            verify(forms, never()).serviceExists(any());
        }
    }
}
