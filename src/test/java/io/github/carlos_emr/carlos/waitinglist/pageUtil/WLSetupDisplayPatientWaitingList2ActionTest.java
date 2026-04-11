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
package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link WLSetupDisplayPatientWaitingList2Action}.
 *
 * <p>Covers security enforcement (missing privilege), demographic_no validation
 * (null, empty, non-numeric), demographic existence check (not found),
 * and the trust boundary fix: only a validated integer string is stored in
 * the HttpSession — never the raw request parameter.</p>
 *
 * @since 2026-04-06
 */
@DisplayName("WLSetupDisplayPatientWaitingList2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("waitinglist")
class WLSetupDisplayPatientWaitingList2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    private WLSetupDisplayPatientWaitingList2Action action;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private WaitingListDao mockWaitingListDao;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DemographicManager.class, mockDemographicManager);
        replaceSpringUtilsBean(WaitingListDao.class, mockWaitingListDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        // WaitingListDao: return empty list by default so the handler does not NPE
        when(mockWaitingListDao.findByDemographic(any())).thenReturn(Collections.emptyList());

        action = new WLSetupDisplayPatientWaitingList2Action();

        // Inject securityInfoManager mock via reflection — field is initialised at declaration time
        java.lang.reflect.Field secField =
                WLSetupDisplayPatientWaitingList2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
    }

    // -------------------------------------------------------------------------
    // Security Enforcement
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("should throw RuntimeException when user lacks demographic read privilege")
        void shouldThrowRuntimeException_whenUserLacksDemographicReadPrivilege() {
            denyPrivilege("_demographic", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("missing required sec object (_demographic)");

            verifySecurityCheck("_demographic", "r");
        }
    }

    // -------------------------------------------------------------------------
    // demographic_no Parameter Validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("demographic_no Parameter Validation")
    class DemographicNoValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is null")
        void shouldReturnError_whenDemographicNoIsNull() throws Exception {
            // No parameter set — request.getParameter returns null

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is empty")
        void shouldReturnError_whenDemographicNoIsEmpty() throws Exception {
            addRequestParameter("demographic_no", "");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is whitespace only")
        void shouldReturnError_whenDemographicNoIsWhitespace() throws Exception {
            addRequestParameter("demographic_no", "   ");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is non-numeric")
        void shouldReturnError_whenDemographicNoIsNonNumeric() throws Exception {
            addRequestParameter("demographic_no", "abc");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no contains script injection")
        void shouldReturnError_whenDemographicNoContainsScriptInjection() throws Exception {
            addRequestParameter("demographic_no", "<script>alert(1)</script>");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no contains SQL injection")
        void shouldReturnError_whenDemographicNoContainsSqlInjection() throws Exception {
            addRequestParameter("demographic_no", "1 OR 1=1");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is zero")
        void shouldReturnError_whenDemographicNoIsZero() throws Exception {
            addRequestParameter("demographic_no", "0");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is negative")
        void shouldReturnError_whenDemographicNoIsNegative() throws Exception {
            addRequestParameter("demographic_no", "-1");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    // -------------------------------------------------------------------------
    // Demographic Record Loading
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Demographic Record Loading")
    class DemographicRecordLoading {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic record is not found")
        void shouldReturnError_whenDemographicNotFound() throws Exception {
            addRequestParameter("demographic_no", "99999");
            when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), eq("99999")))
                    .thenReturn(null);

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    // -------------------------------------------------------------------------
    // Trust Boundary Fix — Session Attributes Must Use Validated Value
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Trust Boundary — Session Storage")
    class TrustBoundarySessionStorage {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should store validated integer string in session, not raw request param")
        void shouldStoreValidatedIntegerString_inSession() throws Exception {
            // Raw parameter has leading/trailing whitespace to verify normalisation
            addRequestParameter("demographic_no", " 42 ");

            Demographic mockDemo = createMockDemographic("Smith", "John", "M");
            when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), eq("42")))
                    .thenReturn(mockDemo);

            String result = executeAction(action);

            assertThat(result).isEqualTo("continue");
            // Session must hold the normalised integer string, never the raw " 42 "
            Object sessionDemoNo = getMockSession().getAttribute("demographicNo");
            assertThat(sessionDemoNo).isEqualTo("42");
            assertThat(sessionDemoNo).isNotEqualTo(" 42 ");
        }

        @Test
        @DisplayName("should set demoInfo and patientWaitingList session attributes on success")
        void shouldSetDemoInfoAndPatientWaitingList_onSuccess() throws Exception {
            addRequestParameter("demographic_no", "100");

            Demographic mockDemo = createMockDemographic("Doe", "Jane", "F");
            when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), eq("100")))
                    .thenReturn(mockDemo);

            String result = executeAction(action);

            assertThat(result).isEqualTo("continue");
            assertThat(getMockSession().getAttribute("demoInfo")).isNotNull();
            assertThat(getMockSession().getAttribute("patientWaitingList")).isNotNull();
            assertThat(getMockSession().getAttribute("demographicNo")).isEqualTo("100");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a minimal {@link Demographic} stub sufficient for the action's
     * {@code getLastName() + ", " + getFirstName() + " " + getSex() + " " + getAge()} call.
     */
    private Demographic createMockDemographic(String lastName, String firstName, String sex) {
        Demographic demo = new Demographic();
        demo.setLastName(lastName);
        demo.setFirstName(firstName);
        demo.setSex(sex);
        // YearOfBirth/MonthOfBirth/DateOfBirth left null — getAge() returns "" for nulls
        return demo;
    }
}
