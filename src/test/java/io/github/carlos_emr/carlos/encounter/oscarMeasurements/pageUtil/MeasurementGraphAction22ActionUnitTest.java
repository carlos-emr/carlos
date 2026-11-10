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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MeasurementGraphAction22Action} logout redirect short-circuiting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeasurementGraphAction22Action logout redirect")
@Tag("unit")
@Tag("clinical")
class MeasurementGraphAction22ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @ParameterizedTest
    @ValueSource(strings = {"0.1", "100", "-5.25", "0"})
    void shouldPreserveExactNumericResults_forNumericInput(String result) {
        assertThat(MeasurementGraphAction22Action.numericResult(result)).isEqualTo(result);
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return NONE when graph session is unauthenticated")
    void shouldReturnNone_whenGraphSessionUnauthenticated() throws Exception {
        MeasurementGraphAction22Action action = new MeasurementGraphAction22Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
        assertThat(response.getContentType()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"actualLab", "lab", "ChartMeds", ""})
    @DisplayName("should refuse a locked patient's graph before drawing any of the patient's data")
    void shouldRefuseGraph_whenPatientRecordIsLocked(String method) {
        LoggedInInfo user = new LoggedInInfo();
        request.getSession().setAttribute("userrole", "doctor");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        request.setParameter("demographic_no", "42");
        if (!method.isEmpty()) request.setParameter("method", method);
        org.mockito.Mockito.lenient().when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString()))
                .thenReturn(true);
        org.mockito.Mockito.when(securityInfoManager.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), org.mockito.ArgumentMatchers.eq(42)))
                .thenReturn(false);
        try (MockedStatic<io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString> names =
                     mockStatic(io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString.class)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MeasurementGraphAction22Action().execute())
                    .isInstanceOf(SecurityException.class);
            names.verifyNoInteractions();
        }
        assertThat(response.getContentType()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"<0.1,0.1", ">100,100", "<=5,5", ">=10,10", "≤5,5", "≥10,10",
            "5 mmol/L,5", "5%,5", "'1,234',1234", "+5.2,5.2", "H 7.4,7.4"})
    @DisplayName("should plot the number in a qualified result, as OSCAR 19 does")
    void shouldPlotTheNumberOfAQualifiedResult_inNumericSeries(String value, String plotted) {
        assertThat(MeasurementGraphAction22Action.numericResult(value)).isEqualTo(plotted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Negative", "Pending", "NaN", "Infinity", "+", "-", ".", "+.", "5..2", "3-5"})
    @DisplayName("should leave out a result that holds no single number")
    void shouldExcludeResultsWithoutOneNumber_fromNumericSeries(String value) {
        assertThat(MeasurementGraphAction22Action.numericResult(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.2", ".5", "137", "145", "5.7"})
    void shouldPreservePlainNumbers_inNumericSeries(String value) {
        assertThat(MeasurementGraphAction22Action.numericResult("  " + value + "  ")).isEqualTo(value);
    }

    @Test
    void shouldExcludeLongMalformedAndOverflowValues_fromNumericSeries() {
        assertThat(MeasurementGraphAction22Action.numericResult("1".repeat(1000000) + "x")).isNull();
        assertThat(MeasurementGraphAction22Action.numericResult("1".repeat(10000))).isNull();
    }
}
