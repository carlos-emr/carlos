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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBean;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListBean;
import io.github.carlos_emr.carlos.dxresearch.bean.dxQuickListBeanHandler;
import io.github.carlos_emr.carlos.managers.CodingSystemManager;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.model.DxRegistedPTInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

/**
 * Unit tests for {@link DxresearchReport2Action} input validation.
 *
 * <p>Verifies that the {@code provider_no} request parameter is validated
 * before its value crosses the trust boundary into the HttpSession (CWE-501),
 * and that the {@code codesearch} parameter is validated in {@code addSearchCode}.</p>
 *
 * @since 2026-04-08
 */
@DisplayName("DxresearchReport2Action Input Validation Tests")
@Tag("unit")
@Tag("web")
@Tag("dxresearch")
class DxresearchReport2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    @Mock
    private DxresearchDAO mockDxresearchDAO;

    @Mock
    private MyGroupDao mockMyGroupDao;

    private DxresearchReport2Action action;
    private CodingSystemManager codingSystemManager;

    @BeforeEach
    void setUp() throws Exception {
        // Note: MockitoAnnotations.openMocks() is already called by CarlosTestBase.setUpBase();
        // calling it again here would cause redundant mock initialization.

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DxresearchDAO.class, mockDxresearchDAO);
        replaceSpringUtilsBean(MyGroupDao.class, mockMyGroupDao);
        codingSystemManager = mock(CodingSystemManager.class);
        replaceSpringUtilsBean(CodingSystemManager.class, codingSystemManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DxresearchReport2Action();
    }

    // ── patientRegistedAll ──────────────────────────────────────────────

    @Nested
    @DisplayName("patientRegistedAll provider_no Validation")
    class PatientRegistedAllValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is null")
        void shouldReturnError_whenProviderNoIsNull() throws Exception {
            addRequestParameter("method", "patientRegistedAll");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when provider_no is empty")
        void shouldReturnError_whenProviderNoIsEmpty() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when provider_no contains XSS payload")
        void shouldReturnError_whenProviderNoContainsXss() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "<script>alert(1)</script>");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when provider_no contains SQL injection")
        void shouldReturnError_whenProviderNoContainsSqlInjection() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "1' OR '1'='1");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when provider_no exceeds 6 characters")
        void shouldReturnError_whenProviderNoTooLong() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "1234567");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when _grp_ group name contains special characters")
        void shouldReturnError_whenGroupNameContainsSpecialChars() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "_grp_<script>x</script>");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid alphanumeric provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "999998");
            when(mockDxresearchDAO.patientRegistedAll(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should accept valid _grp_ prefixed provider_no")
        void shouldAccept_whenProviderNoHasGroupPrefix() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "_grp_team1");
            when(mockMyGroupDao.getGroupDoctors("team1")).thenReturn(List.of("doc1"));
            when(mockDxresearchDAO.patientRegistedAll(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should accept * (All Providers) sentinel value")
        void shouldAccept_whenProviderNoIsAllProvidersSentinel() throws Exception {
            addRequestParameter("method", "patientRegistedAll");
            addRequestParameter("provider_no", "*");
            when(mockDxresearchDAO.patientRegistedAll(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }
    }

    // ── patientExcelReport ──────────────────────────────────────────────

    @Nested
    @DisplayName("patientExcelReport provider_no Validation")
    class PatientExcelReportValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is null")
        void shouldReturnError_whenProviderNoIsNull() throws Exception {
            addRequestParameter("method", "patientExcelReport");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when provider_no contains path traversal")
        void shouldReturnError_whenProviderNoContainsPathTraversal() throws Exception {
            addRequestParameter("method", "patientExcelReport");
            addRequestParameter("provider_no", "../etc/passwd");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientExcelReport");
            addRequestParameter("provider_no", "doc1");
            setSessionAttribute("listview", new ArrayList<>(List.of(new DxRegistedPTInfo(
                    "Test", "Patient", "X", "2000-01-01", "555-0100", "TEST-HIN",
                    "icd9", "250", "2026-01-01", "2026-08-06", "A"))));
            setSessionAttribute("radiovaluestatus", "patientRegistedAll");

            // patientExcelReport returns NONE after writing directly to the output stream
            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            assertThat(getMockResponse().getHeader("Content-disposition"))
                    .isEqualTo("attachment; filename=dxResearchReport.xlsx");
            assertThat(getMockResponse().getContentAsByteArray())
                    .startsWith((byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04);
        }
    }

    // ── patientRegistedActive ───────────────────────────────────────────

    @Nested
    @DisplayName("patientRegistedActive provider_no Validation")
    class PatientRegistedActiveValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is invalid")
        void shouldReturnError_whenProviderNoIsInvalid() throws Exception {
            addRequestParameter("method", "patientRegistedActive");
            addRequestParameter("provider_no", "abc-xyz!");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientRegistedActive");
            addRequestParameter("provider_no", "doc1");
            when(mockDxresearchDAO.patientRegistedActive(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }
    }

    // ── patientRegistedDeleted ──────────────────────────────────────────

    @Nested
    @DisplayName("patientRegistedDeleted provider_no Validation")
    class PatientRegistedDeletedValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is invalid")
        void shouldReturnError_whenProviderNoIsInvalid() throws Exception {
            addRequestParameter("method", "patientRegistedDeleted");
            addRequestParameter("provider_no", "DROP TABLE;");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientRegistedDeleted");
            addRequestParameter("provider_no", "999998");
            when(mockDxresearchDAO.patientRegistedDeleted(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }
    }

    // ── patientRegistedResolve ──────────────────────────────────────────

    @Nested
    @DisplayName("patientRegistedResolve provider_no Validation")
    class PatientRegistedResolveValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is invalid")
        void shouldReturnError_whenProviderNoIsInvalid() throws Exception {
            addRequestParameter("method", "patientRegistedResolve");
            addRequestParameter("provider_no", "a b c");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientRegistedResolve");
            addRequestParameter("provider_no", "999998");
            when(mockDxresearchDAO.patientRegistedResolve(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }
    }

    // ── patientRegistedDistincted ───────────────────────────────────────

    @Nested
    @DisplayName("patientRegistedDistincted provider_no Validation")
    class PatientRegistedDistinctedValidation {

        @Test
        @DisplayName("should return ERROR when provider_no is invalid")
        void shouldReturnError_whenProviderNoIsInvalid() throws Exception {
            addRequestParameter("method", "patientRegistedDistincted");
            addRequestParameter("provider_no", "${jndi:ldap://evil}");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should accept valid provider_no")
        void shouldAccept_whenProviderNoIsValid() throws Exception {
            addRequestParameter("method", "patientRegistedDistincted");
            addRequestParameter("provider_no", "999998");
            when(mockDxresearchDAO.patientRegistedDistincted(any(), any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo("success");
        }
    }

    // ── addSearchCode ───────────────────────────────────────────────────

    @Nested
    @DisplayName("addSearchCode code Validation")
    class AddSearchCodeValidation {

        @Test
        @DisplayName("should return ERROR when codesearch contains XSS")
        void shouldReturnError_whenCodesearchContainsXss() throws Exception {
            addRequestParameter("method", "addSearchCode");
            addRequestParameter("codesearch", "<script>alert(1)</script>");
            addRequestParameter("codesystem", "icd9");

            when(mockDxresearchDAO.getQuickListItems(any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when codesearch exceeds 10 characters")
        void shouldReturnError_whenCodesearchTooLong() throws Exception {
            addRequestParameter("method", "addSearchCode");
            addRequestParameter("codesearch", "12345678901");
            addRequestParameter("codesystem", "icd9");

            when(mockDxresearchDAO.getQuickListItems(any())).thenReturn(new ArrayList<>());

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }
    @Nested
    @DisplayName("Named quick-list report criteria")
    class QuickListCriteria {
        @Test
        void shouldAddEveryNamedListCodeOnce_acrossRepeatedApplications() throws Exception {
            addRequestParameter("method", "addSearchCode");
            action.setQuickListName("Clinic \"A\"&");
            List<dxCodeSearchBean> stored = List.of(code("icd9", "250"), code("icd9", "401"), code("icd9", "250"));
            when(mockDxresearchDAO.getQuickListItems("Clinic \"A\"&")).thenReturn(stored);
            setSessionAttribute("codeSearch", List.of(code("icd9", "401")));

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat((List<?>) mockSession.getAttribute("codeSearch"))
                    .extracting("type", "dxSearchCode").containsExactly(tuple("icd9", "250"), tuple("icd9", "401"));
            assertThat(stored).hasSize(3);
            verify(mockDxresearchDAO, times(2)).getQuickListItems("Clinic \"A\"&");
        }

        @ParameterizedTest
        @ValueSource(strings = {"icd9", " ICD9 "})
        void shouldPreserveSameNumberInDifferentCodingSystems_whenMergingManualAndListCodes(String system) throws Exception {
            addRequestParameter("method", "addSearchCode");
            addRequestParameter("codesystem", system);
            addRequestParameter("codesearch", "250");
            action.setQuickListName("Diabetes");
            when(mockDxresearchDAO.getQuickListItems("Diabetes")).thenReturn(List.of(code("icd9", "250")));
            when(codingSystemManager.getCodeDescription("icd9", "250")).thenReturn("Diabetes mellitus");
            setSessionAttribute("codeSearch", List.of(code("icd10", "250")));

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat((List<?>) mockSession.getAttribute("codeSearch"))
                    .extracting("type", "dxSearchCode").containsExactly(tuple("icd9", "250"), tuple("icd10", "250"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "AbC", "ABC"})
        void shouldMergeAlphabeticCodeIgnoringCase_withoutMergingCodingSystems(String manualCode) throws Exception {
            addRequestParameter("method", "addSearchCode");
            addRequestParameter("codesystem", "ichppccode");
            addRequestParameter("codesearch", manualCode);
            action.setQuickListName("Alphabetic");
            when(mockDxresearchDAO.getQuickListItems("Alphabetic"))
                    .thenReturn(List.of(code("ichppccode", "ABC")));
            when(codingSystemManager.getCodeDescription("ichppccode", manualCode)).thenReturn("Alphabetic code");
            setSessionAttribute("codeSearch", List.of(code("ichppccode", "abc"), code("icd10", "ABC")));

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat((List<?>) mockSession.getAttribute("codeSearch"))
                    .extracting("type", "dxSearchCode")
                    .containsExactly(tuple("ichppccode", "ABC"), tuple("icd10", "ABC"));
        }

        @Test
        void shouldRetainExistingCriteria_whenSelectedListHasNoItems() throws Exception {
            addRequestParameter("method", "addSearchCode");
            action.setQuickListName("retired");
            when(mockDxresearchDAO.getQuickListItems("retired")).thenReturn(List.of());
            setSessionAttribute("codeSearch", List.of(code("icd9", "401")));

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat((List<?>) mockSession.getAttribute("codeSearch"))
                    .extracting("type", "dxSearchCode").containsExactly(tuple("icd9", "401"));
        }

        private dxCodeSearchBean code(String system, String value) {
            dxCodeSearchBean result = new dxCodeSearchBean("Description " + value, value);
            result.setType(system);
            return result;
        }
    }

    /**
     * Issue #4547. The patient Disease Registry ({@code dxSetupResearch2Action}) stores, under the
     * shared {@code allQuickLists} session key, a per-provider handler that marks a default list
     * "Selected". While the report rendered that key, its quick-list selector came back preselected
     * after any re-render, and the next single-code Add posted the whole list with it. The report
     * now builds its own unselected handler as a request attribute for every render.
     */
    @Nested
    @DisplayName("Report quick-list selector isolation (issue #4547)")
    class ReportQuickListIsolation {

        private QuickListDao quickListDao;

        @BeforeEach
        void setUpQuickLists() {
            quickListDao = mock(QuickListDao.class);
            replaceSpringUtilsBean(QuickListDao.class, quickListDao);
            when(quickListDao.findDistinct()).thenReturn(List.of("Diabetes", "Hypertension"));
            when(quickListDao.findByCodingSystem(null)).thenReturn(List.of(
                    quickList("Diabetes", "250"), quickList("Hypertension", "401")));
        }

        @Test
        @DisplayName("should render the report's own unselected quick lists when the report opens")
        void shouldRenderUnselectedReportQuickLists_whenReportOpens() throws Exception {
            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(reportQuickListNames()).containsExactly(tuple("Diabetes", ""), tuple("Hypertension", ""));
            assertThat(mockSession.getAttribute(DxresearchReport2Action.REPORT_QUICK_LISTS)).isNull();
        }

        @Test
        @DisplayName("should rebuild the quick lists on a re-render, listing one created since the report opened")
        void shouldListCurrentQuickLists_whenReportRerendersAfterAdd() throws Exception {
            when(quickListDao.findDistinct()).thenReturn(List.of("Diabetes", "Hypertension", "Asthma"));
            addRequestParameter("method", "addSearchCode");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(reportQuickListNames())
                    .containsExactly(tuple("Diabetes", ""), tuple("Hypertension", ""), tuple("Asthma", ""));
        }

        @Test
        @DisplayName("should leave the patient registry's quick lists untouched when the report opens")
        void shouldLeaveRegistryQuickListsUntouched_whenReportOpens() throws Exception {
            dxQuickListBeanHandler registryLists = new dxQuickListBeanHandler(TEST_PROVIDER);
            setSessionAttribute("allQuickLists", registryLists);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(mockSession.getAttribute("allQuickLists")).isSameAs(registryLists);
            assertThat(mockRequest.getAttribute(DxresearchReport2Action.REPORT_QUICK_LISTS))
                    .isNotSameAs(registryLists);
        }

        /**
         * An invariant rather than a regression: {@code addSearchCode} never read the session's
         * handler, before or after the fix. The defect was the page posting a preselected list;
         * this pins that the server adds only what is posted.
         */
        @Test
        @DisplayName("should ignore session quick lists when no quick-list name is posted")
        void shouldIgnoreSessionQuickLists_whenNoQuickListNamePosted() throws Exception {
            dxQuickListBeanHandler registryLists = new dxQuickListBeanHandler(TEST_PROVIDER);
            assertThat(registryLists.getDxQuickListBeanVector())
                    .as("precondition: the registry handler marks a default list")
                    .extracting("lastUsed").contains("Selected");
            setSessionAttribute("allQuickLists", registryLists);
            addRequestParameter("method", "addSearchCode");
            addRequestParameter("codesystem", "icd9");
            addRequestParameter("codesearch", "401");
            when(codingSystemManager.getCodeDescription("icd9", "401")).thenReturn("Essential hypertension");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat((List<?>) mockSession.getAttribute("codeSearch"))
                    .extracting("type", "dxSearchCode").containsExactly(tuple("icd9", "401"));
            verify(mockDxresearchDAO, never()).getQuickListItems(argThat(name -> name != null && !name.isEmpty()));
        }

        @Test
        @DisplayName("should not build quick lists when the Excel export streams the response")
        void shouldNotBuildQuickLists_whenExcelExportStreams() throws Exception {
            addRequestParameter("method", "patientExcelReport");
            addRequestParameter("provider_no", TEST_PROVIDER);
            setSessionAttribute("listview", new ArrayList<>(List.of(new DxRegistedPTInfo(
                    "Test", "Patient", "X", "2000-01-01", "555-0100", "TEST-HIN",
                    "icd9", "250", "2026-01-01", "2026-08-06", "A"))));
            setSessionAttribute("radiovaluestatus", "patientRegistedAll");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockRequest.getAttribute(DxresearchReport2Action.REPORT_QUICK_LISTS)).isNull();
            verifyNoInteractions(quickListDao);
        }

        private List<Tuple> reportQuickListNames() {
            assertThat(mockRequest.getAttribute(DxresearchReport2Action.REPORT_QUICK_LISTS))
                    .isInstanceOf(dxQuickListBeanHandler.class);
            dxQuickListBeanHandler handler =
                    (dxQuickListBeanHandler) mockRequest.getAttribute(DxresearchReport2Action.REPORT_QUICK_LISTS);
            return handler.getDxQuickListBeanVector().stream()
                    .map(bean -> tuple(((dxQuickListBean) bean).getQuickListName(), ((dxQuickListBean) bean).getLastUsed()))
                    .toList();
        }

        private QuickList quickList(String name, String code) {
            QuickList row = new QuickList();
            row.setQuickListName(name);
            row.setCreatedByProvider(TEST_PROVIDER);
            row.setDxResearchCode(code);
            row.setCodingSystem("icd9");
            return row;
        }
    }

}
