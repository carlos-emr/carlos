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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Date;

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

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingBCDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * {@link ViewWCB2Action}: what the BC WCB form page (formwcb.jsp) is given to show.
 *
 * <p>The page reads its fields from a {@link WCBForm} request attribute named "WCBForm". The action
 * never set one, a patient without a postal code failed before the page, and an existing form failed
 * on a by-type lookup of a DAO the web context holds twice (issue #4864).</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("ViewWCB2Action")
class ViewWCB2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "42";
    private static final String PROVIDER = "97001";

    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LegacyJdbcQuery> jdbc;
    private DemographicManager demographics;
    private BillingmasterDAO billingmasterDao;

    @BeforeEach
    void wire() throws Exception {
        request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        LoggedInInfo login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), eq("r"), any())).thenReturn(true);
        demographics = createAndRegisterMock(DemographicManager.class);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        // The new-form branch asks BillingFormData for the Side list (empty here).
        createAndRegisterMock(DiagnosticCodeDao.class);
        createAndRegisterMock(BillingBCDao.class);

        // The billing provider's practitioner and payee numbers come from a plain JDBC lookup.
        ResultSet provider = mock(ResultSet.class);
        when(provider.next()).thenReturn(true);
        when(provider.getString("ohip_no")).thenReturn("91234");
        when(provider.getString("billing_no")).thenReturn("81234");
        jdbc = mockStatic(LegacyJdbcQuery.class);
        jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(anyString(), eq(PROVIDER))).thenReturn(provider);
    }

    @AfterEach
    void close() {
        jdbc.close();
        servlet.close();
    }

    private Demographic patient(String postal) {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(Integer.valueOf(PATIENT));
        demographic.setFirstName("Workflow");
        demographic.setLastName("FAKE-PW-test");
        demographic.setSex("F");
        demographic.setPhone("604-555-0123");
        demographic.setPostal(postal);
        demographic.setHin("9000000042");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setAddress("1 FAKE Street");
        demographic.setCity("Vancouver");
        when(demographics.getDemographic(any(), eq(PATIENT))).thenReturn(demographic);
        return demographic;
    }

    private WCBForm open(String formId) {
        request.setParameter("demographic_no", PATIENT);
        request.setParameter("provNo", PROVIDER);
        request.setParameter("formId", formId);
        assertThat(new ViewWCB2Action().execute()).isEqualTo("success");
        Object form = request.getAttribute("WCBForm");
        assertThat(form).as("the WCBForm request attribute the page reads").isInstanceOf(WCBForm.class);
        return (WCBForm) form;
    }

    @Test
    @DisplayName("should give a new form the patient's details and the billing provider's numbers")
    void shouldPrefillNewForm_fromPatientAndProvider() {
        patient("V6B 1A1");

        WCBForm form = open("0");

        assertThat(form.getDemographic()).isEqualTo(PATIENT);
        assertThat(form.getW_fname()).isEqualTo("Workflow");
        assertThat(form.getW_lname()).isEqualTo("FAKE-PW-test");
        assertThat(form.getW_gender()).isEqualTo("F");
        assertThat(form.getW_postal()).isEqualTo("V6B1A1");
        assertThat(form.getW_phn()).isEqualTo("9000000042");
        assertThat(form.getW_dob()).isEqualTo("1980-01-02");
        assertThat(form.getW_address()).isEqualTo("1 FAKE Street");
        assertThat(form.getW_city()).isEqualTo("Vancouver");
        assertThat(form.getW_pracno()).isEqualTo("91234");
        assertThat(form.getW_payeeno()).isEqualTo("81234");
        assertThat(form.getProviderNo()).isEqualTo(PROVIDER);
        assertThat(form.getFormNeeded()).isEqualTo("1");
    }

    @Test
    @DisplayName("should open a new form for a patient without a postal code")
    void shouldOpenNewForm_forPatientWithoutPostalCode() {
        patient(null);

        WCBForm form = open("0");

        assertThat(form.getW_postal()).isEmpty();
        assertThat(form.getW_lname()).isEqualTo("FAKE-PW-test");
    }

    @Test
    @DisplayName("should give an existing form its saved values, read only")
    void shouldShowSavedValues_forExistingForm() {
        WCB saved = new WCB();
        saved.setDemographic_no(Integer.parseInt(PATIENT));
        saved.setW_lname("FAKE-PW-saved");
        saved.setW_reporttype("C");
        saved.setW_icd9("780");
        saved.setW_side("L");
        saved.setW_problem("FAKE problem text");
        saved.setW_duration(2);
        saved.setFormNeeded(1);
        saved.setFormCreated(new Date());
        saved.setFormEdited(new Date());
        when(billingmasterDao.getWCBForm("7")).thenReturn(saved);

        WCBForm form = open("7");

        assertThat(request.getAttribute("readonly")).isEqualTo("true");
        assertThat(form.getDemographic()).isEqualTo(PATIENT);
        assertThat(form.getW_lname()).isEqualTo("FAKE-PW-saved");
        assertThat(form.getW_reporttype()).isEqualTo("C");
        assertThat(form.getW_icd9()).isEqualTo("780");
        assertThat(form.getW_side()).isEqualTo("L");
        assertThat(form.getW_problem()).isEqualTo("FAKE problem text");
        assertThat(form.getW_duration()).isEqualTo("2");
        assertThat(form.getFormNeeded()).isEqualTo("true");
    }
}
