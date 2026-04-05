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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link DemographicEdit2Action}.
 *
 * <p>Covers security enforcement (null session, missing privilege),
 * session expiry (null "user" attribute returns "logout"),
 * demographic_no validation (null, empty, non-numeric),
 * and null demographic record handling.</p>
 *
 * @since 2026-04-04
 */
@DisplayName("DemographicEdit2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicEdit2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private DemographicEdit2Action action;

    @Mock
    private DemographicDao mockDemographicDao;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicEdit2Action();

        // Inject mocks via reflection (fields initialized at declaration time)
        java.lang.reflect.Field secField = DemographicEdit2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
    }

    @Nested
    @DisplayName("Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("should throw SecurityException when session is null")
        void shouldThrowSecurityException_whenSessionIsNull() {
            String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
            setSessionAttribute(key, null);

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required session");
        }

        @Test
        @DisplayName("should throw SecurityException when user lacks demographic read privilege")
        void shouldThrowSecurityException_whenUserLacksReadPrivilege() {
            denyPrivilege("_demographic", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_demographic)");

            verifySecurityCheck("_demographic", "r");
        }

        @Test
        @DisplayName("should require read privilege for demographic edit")
        void shouldRequireReadPrivilege_forDemographicEdit() {
            denyPrivilege("_demographic", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);

            verify(mockSecurityInfoManager).hasPrivilege(
                    any(LoggedInInfo.class), eq("_demographic"), eq("r"), any());
        }
    }

    @Nested
    @DisplayName("Session Expiry")
    class SessionExpiry {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return logout when user session attribute is null")
        void shouldReturnLogout_whenUserSessionAttributeIsNull() throws Exception {
            setSessionAttribute("user", null);

            String result = executeAction(action);

            assertThat(result).isEqualTo("logout");
        }
    }

    @Nested
    @DisplayName("Demographic Number Validation")
    class DemographicNoValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is null")
        void shouldReturnError_whenDemographicNoIsNull() throws Exception {
            // No demographic_no parameter set

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
    }

    @Nested
    @DisplayName("Demographic Record Loading")
    class DemographicRecordLoading {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic record not found")
        void shouldReturnError_whenDemographicNotFound() throws Exception {
            addRequestParameter("demographic_no", "99999");
            when(mockDemographicDao.getDemographic("99999")).thenReturn(null);

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }
}
