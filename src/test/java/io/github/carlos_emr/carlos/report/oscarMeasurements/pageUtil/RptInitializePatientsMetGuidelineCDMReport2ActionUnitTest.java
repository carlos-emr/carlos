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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.dao.forms.FormsDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the request guards on the CDM "patients who met guideline" report: the
 * submitted above/below operator selects a constant statement and is never concatenated, and a
 * row's hidden type/instruction fields must match what the server rendered for that row.
 */
@Tag("unit")
@Tag("report")
@Tag("security")
@DisplayName("RptInitializePatientsMetGuidelineCDMReport2Action request guards")
class RptInitializePatientsMetGuidelineCDMReport2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should accept the two operators the met-guideline form offers")
    void shouldAcceptFormOperators_forAboveAndBelow() {
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator(">")).isEqualTo(">");
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator("<")).isEqualTo("<");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "=", ">=", "<=", " >", "> ", "<>", "> 0 OR 1=1 OR dataField >", "<' OR '1'='1", ";DROP TABLE measurements;--"})
    @DisplayName("should reject any other comparator, including injection payloads")
    void shouldReturnNull_forUnsupportedComparator(String raw) {
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.guidelineComparator(raw)).isNull();
    }

    @Test
    @DisplayName("should bind the guideline and use only a fixed operator in every statement")
    void shouldUseFixedOperatorAndBoundGuideline_inEveryStatement() {
        List<String> statements = List.of(
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE_WITH_INSTRUCTION,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW_WITH_INSTRUCTION,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE,
                RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW);
        assertThat(statements).isNotEmpty().allSatisfy(sql -> assertThat(sql).matches(".* AND dataField [<>] :guideline"));
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_ABOVE_WITH_INSTRUCTION)
                .contains("measuringInstruction = :measuringInstruction").endsWith("> :guideline");
        assertThat(RptInitializePatientsMetGuidelineCDMReport2Action.SQL_MET_BELOW).endsWith("< :guideline")
                .doesNotContain("measuringInstruction");
    }

    /**
     * Drives {@code execute()} with the raw {@code value(...)} request parameters, as Tomcat
     * delivers them, against a session whose {@code measurementTypes} lists one AACP row.
     */
    @Nested
    @DisplayName("tampered row selectors")
    class TamperedSelectors {

        private static final String GROUP = "FAKE-OMD-CDM";
        private static final String CURRENT = "Provided/Revised/Reviewed";

        private MockedStatic<ServletActionContext> servletActionContext;
        private MockedStatic<LoggedInInfo> loggedInInfo;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private MeasurementDao measurementDao;
        private FormsDao formsDao;
        private MeasurementTypeDao typeDao;
        private ValidationsDao validationsDao;

        @BeforeEach
        void setUp() {
            SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
            MeasurementGroupDao groupDao = createAndRegisterMock(MeasurementGroupDao.class);
            typeDao = createAndRegisterMock(MeasurementTypeDao.class);
            validationsDao = createAndRegisterMock(ValidationsDao.class);
            measurementDao = createAndRegisterMock(MeasurementDao.class);
            formsDao = createAndRegisterMock(FormsDao.class);

            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);
            HttpSession session = mock(HttpSession.class);
            when(request.getSession(false)).thenReturn(session);
            servletActionContext = mockStatic(ServletActionContext.class);
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
            LoggedInInfo loggedIn = mock(LoggedInInfo.class);
            loggedInInfo = mockStatic(LoggedInInfo.class);
            loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedIn);
            when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_report"), eq("r"), isNull()))
                    .thenReturn(true);

            MeasurementGroup groupRow = new MeasurementGroup();
            groupRow.setName(GROUP);
            groupRow.setTypeDisplayName("Asthma Action Plan");
            when(groupDao.findByName(GROUP)).thenReturn(new ArrayList<>(List.of(groupRow)));
            MeasurementType aacp = mock(MeasurementType.class);
            when(aacp.getId()).thenReturn(6);
            when(aacp.getType()).thenReturn("AACP");
            when(aacp.getTypeDisplayName()).thenReturn("Asthma Action Plan");
            when(aacp.getTypeDescription()).thenReturn("Asthma Action Plan");
            when(aacp.getMeasuringInstruction()).thenReturn(CURRENT);
            when(aacp.getValidation()).thenReturn("18");
            when(typeDao.findByTypeDisplayName("Asthma Action Plan")).thenReturn(List.of(aacp));
            when(measurementDao.findDistinctMeasuringInstructionsByTypes(anyCollection()))
                    .thenReturn(Map.of("AACP", List.of("Yes/No")));
            // The same definitions the CDM setup page stores for the report forms, built before the
            // session stub so its DAO calls are not caught inside an in-progress when().
            RptMeasurementTypesBeanHandler definitions = new RptMeasurementTypesBeanHandler(GROUP);
            when(session.getAttribute("measurementTypes")).thenReturn(definitions);
            clearInvocations(measurementDao);
        }

        @AfterEach
        void tearDown() {
            loggedInInfo.close();
            servletActionContext.close();
        }

        private RptInitializePatientsMetGuidelineCDMReport2Action actionForRow0(String type, String instruction) {
            return actionForRow(0, type, instruction);
        }

        private RptInitializePatientsMetGuidelineCDMReport2Action actionForRow(int row, String type, String instruction) {
            when(request.getParameter("value(measurementType" + row + ")")).thenReturn(type);
            when(request.getParameter("value(mNbInstrcs" + row + ")")).thenReturn("1");
            when(request.getParameter("value(mInstrcsCheckbox" + row + "0)")).thenReturn(instruction);
            when(request.getParameter("value(aboveBelow" + row + ")")).thenReturn(">");
            RptInitializePatientsMetGuidelineCDMReport2Action action = new TestableAction();
            action.setGuidelineCheckbox(new String[] {Integer.toString(row)});
            action.setStartDateB(new String[] {"2025-01-01"});
            action.setEndDateB(new String[] {"2026-01-01"});
            action.setGuidelineB(new String[] {"Provided"});
            return action;
        }

        private RptInitializePatientsMetGuidelineCDMReport2Action actionForRawIndex(String rawIndex) {
            when(request.getParameter("value(measurementType0)")).thenReturn("AACP");
            when(request.getParameter("value(mNbInstrcs0)")).thenReturn("1");
            when(request.getParameter("value(mInstrcsCheckbox00)")).thenReturn("Yes/No");
            when(request.getParameter("value(aboveBelow0)")).thenReturn(">");
            RptInitializePatientsMetGuidelineCDMReport2Action action = new TestableAction();
            action.setGuidelineCheckbox(new String[] {rawIndex});
            action.setStartDateB(new String[] {"2025-01-01"});
            action.setEndDateB(new String[] {"2026-01-01"});
            action.setGuidelineB(new String[] {"Provided"});
            return action;
        }

        private void useNumericValidation() {
            MeasurementType type = mock(MeasurementType.class);
            when(type.getValidation()).thenReturn("18");
            when(typeDao.findByType("AACP")).thenReturn(List.of(type));
            Validations numeric = new Validations();
            numeric.setNumeric(true);
            when(validationsDao.find((Object) Integer.valueOf(18))).thenReturn(numeric);
        }

        @Test
        void shouldBindNumbersAndFullTimestamps_whenReportingNumericReadings() throws Exception {
            useNumericValidation();
            Timestamp entered = Timestamp.valueOf("2025-06-01 14:30:12");
            List<Object[]> latest = java.util.Collections.singletonList(new Object[] {123, entered});
            when(measurementDao.findLastEntered(any(Date.class), any(Date.class), eq("AACP"), eq("Yes/No")))
                    .thenReturn(latest);
            when(measurementDao.findLastEntered(any(Date.class), any(Date.class), eq("AACP")))
                    .thenReturn(latest);
            when(formsDao.runParameterizedNativeQuery(anyString(), any(Object[].class))).thenAnswer(call -> {
                Object[] parameters = (Object[]) call.getRawArguments()[1];
                assertThat(parameters).containsSubsequence("dateEntered", entered)
                        .containsSubsequence("guideline", new BigDecimal("9"));
                return java.util.Collections.singletonList(new Object[] {"10"});
            });
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRow0("AACP", "Yes/No");
            action.setGuidelineB(new String[] {"9"});

            assertThat(action.execute()).isEqualTo("success");
            verify(formsDao, times(2)).runParameterizedNativeQuery(anyString(), any(Object[].class));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "NaN", "Infinity", "not a number"})
        void shouldRejectInvalidNumericGuideline_whenNoInstructionIsSelected(String guideline) throws Exception {
            useNumericValidation();
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRow0("AACP", null);
            action.setGuidelineB(new String[] {guideline});

            assertThat(action.execute()).isEqualTo("none");
            verifyNoInteractions(formsDao);
            verify(measurementDao, never()).findLastEntered(any(Date.class), any(Date.class), anyString());
        }

        @Test
        @DisplayName("should bound the instruction loop by the rendered list when a huge count is posted")
        void shouldIgnorePostedCount_whenInstructionCountIsHuge() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRow0("AACP", "Yes/No");
            when(request.getParameter("value(mNbInstrcs0)")).thenReturn(Integer.toString(Integer.MAX_VALUE));
            when(request.getParameter("value(mInstrcsCheckbox01)")).thenReturn(CURRENT);

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            // Two instructions were rendered for row 0, so validate + report read exactly two
            // checkbox fields each and never probe a third; the DAO sees one query per instruction.
            verify(request, times(2)).getParameter("value(mInstrcsCheckbox00)");
            verify(request, times(2)).getParameter("value(mInstrcsCheckbox01)");
            verify(request, never()).getParameter(startsWith("value(mInstrcsCheckbox02"));
            verify(measurementDao, times(2)).findLastEntered(any(Date.class), any(Date.class), eq("AACP"), anyString());
        }

        @Test
        @DisplayName("should skip a row whose index is not numeric instead of failing with a 500")
        void shouldSkipRow_whenIndexIsNotNumeric() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRawIndex("abc");

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verifyNoInteractions(measurementDao);
            verifyNoInteractions(formsDao);
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("should skip a row whose index is past the rendered rows or the posted arrays")
        void shouldSkipRow_whenIndexIsOutOfRange() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action past = actionForRawIndex("7");
            assertThat(past.execute()).isEqualTo("success");

            RptInitializePatientsMetGuidelineCDMReport2Action negative = actionForRawIndex("-1");
            assertThat(negative.execute()).isEqualTo("success");

            // Row 0 is rendered, but the posted arrays are empty: no array may be indexed.
            RptInitializePatientsMetGuidelineCDMReport2Action shortArrays = actionForRawIndex("0");
            shortArrays.setStartDateB(new String[0]);
            assertThat(shortArrays.execute()).isEqualTo("success");

            verifyNoInteractions(measurementDao);
            verifyNoInteractions(formsDao);
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("should not query any patient data for a row whose type is not the rendered one")
        void shouldSkipRow_whenMeasurementTypeIsTampered() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRow0("HIV", CURRENT);

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verifyNoInteractions(measurementDao);
            verifyNoInteractions(formsDao);
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("should not query with an instruction that is not one rendered for the row")
        void shouldSkipInstruction_whenMeasuringInstructionIsTampered() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action action =
                    actionForRow0("AACP", "FAKE-Patient Smith asked about inhaler");

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verify(measurementDao, never()).findLastEntered(any(Date.class), any(Date.class), anyString(), anyString());
            verifyNoInteractions(formsDao);
        }

        @Test
        @DisplayName("should still report a row whose type and instruction match the rendered ones")
        void shouldQueryRow_whenSelectorsMatchRenderedRow() throws Exception {
            RptInitializePatientsMetGuidelineCDMReport2Action action = actionForRow0("AACP", "Yes/No");

            String result = action.execute();

            assertThat(result).isEqualTo("success");
            verify(measurementDao).findLastEntered(any(Date.class), any(Date.class), eq("AACP"), eq("Yes/No"));
            verify(measurementDao).findLastEntered(any(Date.class), any(Date.class), eq("AACP"));
        }
    }

    /** Resolves message keys without a Struts container. */
    private static final class TestableAction extends RptInitializePatientsMetGuidelineCDMReport2Action {

        @Override
        public String getText(String key) {
            return key;
        }

        @Override
        public String getText(String key, String defaultValue) {
            return key;
        }

        @Override
        public String getText(String key, String[] args) {
            return key;
        }
    }
}
