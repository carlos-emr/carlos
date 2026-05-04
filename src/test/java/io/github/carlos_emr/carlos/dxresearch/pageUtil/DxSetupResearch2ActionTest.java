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
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link dxSetupResearch2Action} input validation.
 *
 * <p>Verifies that unsanitized HTTP request parameters are rejected before
 * any data crosses the trust boundary into the HttpSession (CWE-501).</p>
 *
 * @since 2026-04-06
 */
@DisplayName("dxSetupResearch2Action Input Validation Tests")
@Tag("unit")
@Tag("web")
@Tag("dxresearch")
class DxSetupResearch2ActionTest extends CarlosWebTestBase {

    private static final String VALID_DEMO_NO = "12345";
    private static final String VALID_PROVIDER_NO = "9001";
    private static final String TEST_PROVIDER = "999998";

    private dxSetupResearch2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new dxSetupResearch2Action();

        java.lang.reflect.Field secField = dxSetupResearch2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
    }

    @Nested
    @DisplayName("Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("should throw RuntimeException when user lacks _dxresearch read privilege")
        void shouldThrowRuntimeException_whenUserLacksDxresearchReadPrivilege() {
            denyPrivilege("_dxresearch", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("missing required sec object (_dxresearch)");
        }
    }

    @Nested
    @DisplayName("demographicNo Validation")
    class DemographicNoValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_dxresearch", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographicNo is null")
        void shouldReturnError_whenDemographicNoIsNull() throws Exception {
            // no demographicNo parameter

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographicNo is empty")
        void shouldReturnError_whenDemographicNoIsEmpty() throws Exception {
            addRequestParameter("demographicNo", "");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographicNo is non-numeric")
        void shouldReturnError_whenDemographicNoIsNonNumeric() throws Exception {
            addRequestParameter("demographicNo", "abc");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographicNo contains SQL injection attempt")
        void shouldReturnError_whenDemographicNoContainsSqlInjection() throws Exception {
            addRequestParameter("demographicNo", "1; DROP TABLE demographic;--");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographicNo is negative")
        void shouldReturnError_whenDemographicNoIsNegative() throws Exception {
            addRequestParameter("demographicNo", "-1");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    @Nested
    @DisplayName("providerNo Validation")
    class ProviderNoValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_dxresearch", "r");
        }

        @Test
        @DisplayName("should return ERROR when providerNo is non-numeric")
        void shouldReturnError_whenProviderNoIsNonNumeric() throws Exception {
            addRequestParameter("demographicNo", VALID_DEMO_NO);
            addRequestParameter("providerNo", "not-a-number");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when providerNo contains XSS payload")
        void shouldReturnError_whenProviderNoContainsXssPayload() throws Exception {
            addRequestParameter("demographicNo", VALID_DEMO_NO);
            addRequestParameter("providerNo", "<script>alert(1)</script>");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    @Nested
    @DisplayName("quickList Validation")
    class QuickListValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_dxresearch", "r");
        }

        @Test
        @DisplayName("should return ERROR when quickList is non-numeric")
        void shouldReturnError_whenQuickListIsNonNumeric() throws Exception {
            addRequestParameter("demographicNo", VALID_DEMO_NO);
            addRequestParameter("quickList", "injected-name");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when quickList contains path traversal")
        void shouldReturnError_whenQuickListContainsPathTraversal() throws Exception {
            addRequestParameter("demographicNo", VALID_DEMO_NO);
            addRequestParameter("quickList", "../../etc/passwd");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }
}
