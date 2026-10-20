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

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the LOINC mapping writes of Customize Measurements:
 * {@link EctAddMeasurementMap2Action} (routes AddMeasurementMap and NewMeasurementMap) and
 * {@link EctRemoveMeasurementMap2Action} (RemoveMeasurementMap and RemapMeasurementMap).
 * Both are dual-purpose: a GET without write parameters renders the page (Customize
 * Measurements links and the pages' Search), and a request that would write must be a POST
 * (issue #3335).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("Measurement mapping write actions")
class EctMeasurementMapWrite2ActionsUnitTest extends CarlosUnitTestBase {

    private MeasurementMapDao mapDao;
    private RecycleBinDao recycleBinDao;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        mapDao = mock(MeasurementMapDao.class);
        recycleBinDao = mock(RecycleBinDao.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        registerMock(MeasurementMapDao.class, mapDao);
        registerMock(MeasurementsExtDao.class, mock(MeasurementsExtDao.class));
        registerMock(RecycleBinDao.class, recycleBinDao);
        registerMock(SecurityInfoManager.class, security);

        request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/AddMeasurementMap");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);
        when(mapDao.findByLoincCodeAndLabType(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    @Nested
    @DisplayName("EctAddMeasurementMap2Action")
    class AddMapping {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        void shouldRefuseTheMapping_whenAWriteIsNotPosted(String method) throws Exception {
            request.setMethod(method);
            request.addParameter("identifier", "WT,FLOWSHEET,FAKE Weight");
            request.addParameter("loinc_code", "X12345");

            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo(EctAddMeasurementMap2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(mapDao, security);
        }

        @Test
        void shouldMapTheIdentifier_keepingCommasInTheName() throws Exception {
            request.addParameter("identifier", "WT,FLOWSHEET,FAKE Weight, adult");
            request.addParameter("loinc_code", "X12345");

            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo("success");
            ArgumentCaptor<MeasurementMap> mapping = ArgumentCaptor.forClass(MeasurementMap.class);
            verify(mapDao).persist(mapping.capture());
            assertThat(mapping.getValue().getIdentCode()).isEqualTo("WT");
            assertThat(mapping.getValue().getLabType()).isEqualTo("FLOWSHEET");
            assertThat(mapping.getValue().getName()).isEqualTo("FAKE Weight, adult");
            assertThat(mapping.getValue().getLoincCode()).isEqualTo("X12345");
        }

        @ParameterizedTest
        @CsvSource({"0,0", "'WT,FLOWSHEET,FAKE Weight',0", "0,X12345", "'WT,FLOWSHEET,FAKE Weight',''"})
        void shouldReportFailureWithoutWriting_whenNoneIsSelected(String identifier, String loinc) throws Exception {
            request.addParameter("identifier", identifier);
            request.addParameter("loinc_code", loinc);

            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo("failure");
            verify(mapDao, never()).persist(any());
        }

        @Test
        void shouldReportTheConflict_whenTheCodeIsAlreadyMappedForTheType() throws Exception {
            request.addParameter("identifier", "WT,FLOWSHEET,FAKE Weight");
            request.addParameter("loinc_code", "X12345");
            when(mapDao.findByLoincCodeAndLabType("X12345", "FLOWSHEET")).thenReturn(List.of(new MeasurementMap()));

            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo("failedcheck");
            verify(mapDao, never()).persist(any());
        }

        @Test
        void shouldRenderTheForm_whenPostedWithoutASelection() throws Exception {
            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo("continue");
            verifyNoInteractions(mapDao);
        }

        @ParameterizedTest
        @CsvSource(value = {"'',''", "searchstring,FAKE", "identifier,'WT,FLOWSHEET,FAKE Weight'"})
        void shouldRenderTheFormByGet_whenNoCodeIsNamed(String param, String value) throws Exception {
            request.setMethod("GET");
            if (!param.isEmpty()) {
                request.addParameter(param, value);
            }

            assertThat(new EctAddMeasurementMap2Action().execute()).isEqualTo("continue");
            assertThat(response.getStatus()).isEqualTo(200);
            verifyNoInteractions(mapDao);
        }

        @Test
        void shouldDenyTheFormByGet_withoutEitherAdministratorPrivilege() {
            request.setMethod("GET");
            when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(false);

            EctAddMeasurementMap2Action action = new EctAddMeasurementMap2Action();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            verifyNoInteractions(mapDao);
        }
    }

    @Nested
    @DisplayName("EctRemoveMeasurementMap2Action")
    class RemoveMapping {

        @ParameterizedTest
        @ValueSource(strings = {"GET", "HEAD"})
        void shouldRefuseTheRemoval_whenAWriteIsNotPosted(String method) throws Exception {
            request.setMethod(method);
            request.addParameter("id", "12");

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo(EctRemoveMeasurementMap2Action.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            verifyNoInteractions(mapDao, recycleBinDao, security);
        }

        @Test
        void shouldRenderTheTableByGet_whenNoRowIsNamed() throws Exception {
            request.setMethod("GET");
            request.addParameter("searchstring", "FAKE");

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo("continue");
            assertThat(response.getStatus()).isEqualTo(200);
            verifyNoInteractions(mapDao, recycleBinDao);
        }

        @Test
        void shouldRecordTheRemoval_againstTheSignedInProvider() throws Exception {
            MeasurementMap row = new MeasurementMap();
            row.setId(12);
            row.setIdentCode("WT");
            row.setLoincCode("X12345");
            row.setName("FAKE Weight");
            row.setLabType("FLOWSHEET");
            when(mapDao.find(12)).thenReturn(row);
            request.addParameter("id", "12");
            request.addParameter("provider_no", "1");

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo("success");
            verify(mapDao).remove(12);
            ArgumentCaptor<RecycleBin> entry = ArgumentCaptor.forClass(RecycleBin.class);
            verify(recycleBinDao).persist(entry.capture());
            assertThat(entry.getValue().getProviderNo()).isEqualTo("999998");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "1;2", "12345678901"})
        void shouldReportFailureWithoutWriting_whenNoMappingRowIsChosen(String id) throws Exception {
            request.addParameter("id", id);

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo("failure");
            verifyNoInteractions(mapDao, recycleBinDao);
        }

        @Test
        void shouldMapTheNewCodeThenRemoveTheOldRow_whenRemapIsPosted() throws Exception {
            MeasurementMap row = new MeasurementMap();
            row.setId(12);
            when(mapDao.find(12)).thenReturn(row);
            request.addParameter("id", "12");
            request.addParameter("identifier", "WT");
            request.addParameter("name", "FAKE Weight");
            request.addParameter("type", "FLOWSHEET");
            request.addParameter("loinc_code", "X67890");

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo("success");
            ArgumentCaptor<MeasurementMap> mapping = ArgumentCaptor.forClass(MeasurementMap.class);
            verify(mapDao).persist(mapping.capture());
            assertThat(mapping.getValue().getLoincCode()).isEqualTo("X67890");
            verify(mapDao).remove(12);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "", " "})
        void shouldKeepTheMappingAndReportFailure_whenRemapHasNoCodeSelected(String loinc) throws Exception {
            request.addParameter("id", "12");
            request.addParameter("identifier", "WT");
            request.addParameter("name", "FAKE Weight");
            request.addParameter("type", "FLOWSHEET");
            request.addParameter("loinc_code", loinc);

            assertThat(new EctRemoveMeasurementMap2Action().execute()).isEqualTo("failure");
            verifyNoInteractions(mapDao, recycleBinDao);
        }
    }
}
