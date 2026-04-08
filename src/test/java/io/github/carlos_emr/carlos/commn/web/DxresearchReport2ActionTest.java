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
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
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

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DxresearchDAO.class, mockDxresearchDAO);
        replaceSpringUtilsBean(MyGroupDao.class, mockMyGroupDao);

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
}
