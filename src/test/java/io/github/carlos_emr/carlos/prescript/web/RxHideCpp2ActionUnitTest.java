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
package io.github.carlos_emr.carlos.prescript.web;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RxHideCpp2Action}: the hide-from-CPP flag is a chart mutation, so it is POST-only, validates
 * the drug id, refuses an unknown drug, and authorises the patient who owns the drug (#3908).
 */
@Tag("unit")
@Tag("prescription")
@Tag("security")
@DisplayName("RxHideCpp2Action")
class RxHideCpp2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final int DRUG_ID = 77;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private DrugDao mockDrugDao;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Drug drug;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DrugDao.class, mockDrugDao);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("prescriptId", String.valueOf(DRUG_ID));
        request.setParameter("value", "true");

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        drug = new Drug();
        drug.setId(DRUG_ID);
        drug.setDemographicId(DEMOGRAPHIC_NO);
        when(mockDrugDao.find(DRUG_ID)).thenReturn(drug);
    }

    @AfterEach
    void tearDown() throws Exception {
        servletActionContextMock.close();
        loggedInInfoMock.close();
        mocks.close();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 before any privilege check or drug lookup on a non-POST request")
    void shouldRefuseNonPost_beforeAnySideEffect(String httpMethod) throws Exception {
        request.setMethod(httpMethod);

        String result = new RxHideCpp2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockDrugDao, mockSecurityInfoManager);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"", "abc", "-1", "1234567890", "77;drop"})
    @DisplayName("should answer 400 without a lookup when the drug id is malformed")
    void shouldAnswerBadRequest_whenDrugIdMalformed(String prescriptId) throws Exception {
        request.setParameter("prescriptId", prescriptId);

        String result = new RxHideCpp2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(mockDrugDao);
    }

    @Test
    @DisplayName("should answer 404 and merge nothing when the drug does not exist")
    void shouldAnswerNotFound_whenDrugUnknown() throws Exception {
        when(mockDrugDao.find(DRUG_ID)).thenReturn(null);

        String result = new RxHideCpp2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @Test
    @DisplayName("should refuse the write when the caller may not access the drug's patient")
    void shouldThrowSecurityException_whenPatientRecordAccessDenied() {
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO))).thenReturn(false);

        assertThatThrownBy(() -> new RxHideCpp2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @Test
    @DisplayName("should refuse the write when the caller lacks patient-level _rx update")
    void shouldThrowSecurityException_whenPatientLevelUpdateDenied() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("u"), eq(DEMOGRAPHIC_NO))).thenReturn(false);

        assertThatThrownBy(() -> new RxHideCpp2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        verify(mockDrugDao, never()).merge(any(Drug.class));
    }

    @ParameterizedTest(name = "value={0}")
    @ValueSource(strings = {"true", "false"})
    @DisplayName("should set the flag for the drug's own patient and answer ok")
    void shouldSetHideFromCpp_whenAuthorisedPost(String value) throws Exception {
        request.setParameter("value", value);
        drug.setHideFromCpp(!Boolean.parseBoolean(value));

        String result = new RxHideCpp2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString().trim()).isEqualTo("ok");
        assertThat(drug.getHideFromCpp()).isEqualTo(Boolean.parseBoolean(value));
        verify(mockDrugDao).merge(drug);
        verify(mockSecurityInfoManager).isAllowedAccessToPatientRecord(any(), eq(DEMOGRAPHIC_NO));
    }
}
