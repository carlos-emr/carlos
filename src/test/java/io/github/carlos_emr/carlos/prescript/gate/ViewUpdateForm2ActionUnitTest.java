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
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The drug-form editor gate loads the drug and authorises its own patient before rendering or
 * changing it (#3908).
 *
 * @since 2026-09-24
 */
@DisplayName("ViewUpdateForm2Action")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class ViewUpdateForm2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DRUG_ID = 77;
    private static final int DEMOGRAPHIC_NO = 42;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private DrugDao drugDao;
    private LoggedInInfo loggedInInfo;
    private Drug drug;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/ViewUpdateForm");
        request.addParameter("id", String.valueOf(DRUG_ID));
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        drugDao = mock(DrugDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DrugDao.class, drugDao);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
        drug = new Drug();
        drug.setId(DRUG_ID);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        drug.setDrugForm("Tablet");
        when(drugDao.find(DRUG_ID)).thenReturn(drug);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should render the drug's form for a patient the caller may read")
    void shouldRenderDrugForm_whenPatientReadable() throws Exception {
        assertThat(new ViewUpdateForm2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("drugId")).isEqualTo(DRUG_ID);
        assertThat(request.getAttribute("drugForm")).isEqualTo("Tablet");
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", "r", DEMOGRAPHIC_NO);
        verify(drugDao, never()).merge(any());
    }

    @ParameterizedTest(name = "{0} denied")
    @ValueSource(strings = {"patient read", "record access"})
    @DisplayName("should not render a drug of a patient the caller may not read")
    void shouldRefuseRender_whenDrugPatientNotReadable(String denied) {
        if ("patient read".equals(denied)) {
            when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), eq(DEMOGRAPHIC_NO))).thenReturn(false);
        } else {
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);
        }

        ViewUpdateForm2Action action = new ViewUpdateForm2Action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        assertThat(request.getAttribute("drugForm")).isNull();
    }

    @Test
    @DisplayName("should change the form on a POST that keeps the drug id in the body")
    void shouldUpdateDrugForm_whenPostCarriesId() throws Exception {
        // updateForm.jsp posts id as a hidden input to the concrete URL (no query string).
        request.setMethod("POST");
        request.addParameter("action", "update");
        request.addParameter("drugForm", "Capsule");

        assertThat(new ViewUpdateForm2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(drug.getDrugForm()).isEqualTo("Capsule");
        verify(drugDao).merge(drug);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", "w", DEMOGRAPHIC_NO);
        assertThat(request.getAttribute("drugFormUpdated")).isEqualTo(Boolean.TRUE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a non-POST update before loading the drug")
    void shouldRejectUpdate_whenMethodIsNotPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.addParameter("action", "update");
        request.addParameter("drugForm", "Capsule");

        assertThat(new ViewUpdateForm2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(drugDao, securityInfoManager);
    }

    @Test
    @DisplayName("should refuse an update of a drug whose patient the caller may not write")
    void shouldRefuseUpdate_whenDrugPatientNotWritable() {
        request.setMethod("POST");
        request.addParameter("action", "update");
        request.addParameter("drugForm", "Capsule");
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), eq(DEMOGRAPHIC_NO))).thenReturn(false);

        ViewUpdateForm2Action action = new ViewUpdateForm2Action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(drugDao, never()).merge(any());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "abc", "-1", "1234567890"})
    @DisplayName("should answer 400 for a malformed drug id")
    void shouldReturnBadRequest_whenDrugIdMalformed(String id) throws Exception {
        request.setParameter("id", id);

        assertThat(new ViewUpdateForm2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(drugDao);
    }

    @Test
    @DisplayName("should answer 404 for an unknown drug")
    void shouldReturnNotFound_whenDrugMissing() throws Exception {
        when(drugDao.find(DRUG_ID)).thenReturn(null);

        assertThat(new ViewUpdateForm2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
