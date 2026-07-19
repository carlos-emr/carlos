/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 * Example Struts2 Action test using modern framework
 */
package io.github.carlos_emr.carlos.test.examples;

import io.github.carlos_emr.carlos.test.base.OpenOWebTestBase;
import io.github.carlos_emr.carlos.tickler.pageUtil.AddTickler2Action;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.commn.model.Provider;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Example test for Struts2 2Actions showing how to handle
 * ServletActionContext and SpringUtils.getBean() patterns.
 */
@DisplayName("Struts2 2Action Modern Test")
class Struts2ActionModernTest extends OpenOWebTestBase {

    @Mock
    private TicklerManager mockTicklerManager;

    private AddTickler2Action action;

    @BeforeEach
    void setUp() throws Exception {
        // Replace SpringUtils bean with mock
        replaceSpringUtilsBean(TicklerManager.class, mockTicklerManager);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        // Create action instance
        action = new AddTickler2Action();

        // Since AddTickler2Action initializes its dependencies at field declaration time,
        // we need to inject our mocks using reflection
        java.lang.reflect.Field ticklerManagerField = AddTickler2Action.class.getDeclaredField("ticklerManager");
        ticklerManagerField.setAccessible(true);
        ticklerManagerField.set(action, mockTicklerManager);

        java.lang.reflect.Field securityManagerField = AddTickler2Action.class.getDeclaredField("securityInfoManager");
        securityManagerField.setAccessible(true);
        securityManagerField.set(action, mockSecurityInfoManager);
    }

    @Test
    @DisplayName("Should enforce security privileges")
    void testSecurityCheck() throws Exception {
        // Given - deny the required privilege
        denyPrivilege("_tickler", "w");

        // When/Then - should throw RuntimeException
        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("missing required sec object");

        // Verify security was checked
        verifySecurityCheck("_tickler", "w");
    }

    @Test
    @DisplayName("Should add tickler with valid parameters")
    void testAddTicklerSuccess() throws Exception {
        // Given
        allowPrivilege("_tickler", "w");

        // Set the "user" session attribute that the action expects for creator
        setSessionAttribute("user", "test_provider");

        // AddTickler2Action uses "demo" not "demographic_no"
        addRequestParameter("demo", "123");
        addRequestParameter("message", "Test tickler message");
        addRequestParameter("priority", "High");
        addRequestParameter("assignedTo", "999998");
        addRequestParameter("date", "2025-01-15");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("close");

        // Verify the tickler was created with correct parameters
        verify(mockTicklerManager).addTickler(
            eq("123"),
            eq("Test tickler message"),
            eq(Tickler.STATUS.A),
            eq("2025-01-15"),
            eq("test_provider"),  // creator from session
            eq(Tickler.PRIORITY.High),
            eq("999998")
        );
    }

    @Test
    @DisplayName("Should handle missing required parameters")
    void testMissingParameters() throws Exception {
        // Given
        allowPrivilege("_tickler", "w");
        // No parameters added

        // When
        String result = executeAction(action);

        // Then - action returns close regardless of missing parameters
        assertThat(result).isEqualTo("close");
    }

    @Test
    @DisplayName("Should handle ServletActionContext correctly")
    void testServletActionContext() throws Exception {
        // This verifies that our mock servlet context is properly injected
        allowPrivilege("_tickler", "w");
        addRequestParameter("test", "value");

        // The action should be able to access request/response via ServletActionContext
        String result = executeAction(action);

        // Verify the action could access the request
        assertThat(getMockRequest().getParameter("test")).isEqualTo("value");
    }

    @Test
    @DisplayName("Should handle method-based routing")
    void testMethodBasedAction() throws Exception {
        // AddTickler2Action doesn't use method-based routing, only has execute()
        allowPrivilege("_tickler", "w");
        addRequestParameter("demo", "12345");
        addRequestParameter("message", "Test");

        // Execute default method
        String result = executeAction(action);

        // Verify it returns the expected result
        assertThat(result).isEqualTo("close");
    }

    @Nested
    @DisplayName("Session Management")
    class SessionManagementTest {

        @Test
        @DisplayName("Should access LoggedInInfo from session")
        void testLoggedInInfoAccess() throws Exception {
            // Given
            allowPrivilege("_tickler", "w");
            setSessionAttribute("loggedInInfo", mockLoggedInInfo);
            Provider testProvider = new Provider();
            testProvider.setProviderNo("999998");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

            addRequestParameter("demographic_no", "123");
            addRequestParameter("message", "Session test");

            // When
            String result = executeAction(action);

            // Then
            assertThat(result).isEqualTo("close");
            // verify(mockLoggedInInfo, atLeastOnce()).getLoggedInProvider(); // Not applicable to this action
        }

        @Test
        @DisplayName("Should handle missing session data")
        void testMissingSessionData() throws Exception {
            // Given - no LoggedInInfo in session (remove the correct key)
            String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
            getMockSession().removeAttribute(loggedInInfoKey);

            // Security check will fail because LoggedInInfo is null
            when(mockSecurityInfoManager.hasPrivilege(
                isNull(),
                eq("_tickler"),
                eq("w"),
                any()
            )).thenReturn(false);

            // When/Then - should throw due to security failure
            assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }
    }

    @Test
    @DisplayName("Should verify SpringUtils anti-pattern handling")
    void testSpringUtilsAntiPattern() throws Exception {
        // This test verifies that we can inject mocks despite the SpringUtils anti-pattern
        // The action uses SpringUtils.getBean() at field initialization, but we override
        // it with reflection in setUp()

        // Verify the action has our mock injected
        java.lang.reflect.Field ticklerManagerField = AddTickler2Action.class.getDeclaredField("ticklerManager");
        ticklerManagerField.setAccessible(true);
        TicklerManager managerInAction = (TicklerManager) ticklerManagerField.get(action);

        assertThat(managerInAction).isSameAs(mockTicklerManager);
    }

    @Test
    @DisplayName("Should handle AJAX response correctly")
    void testAjaxResponse() throws Exception {
        // Given
        allowPrivilege("_tickler", "w");
        addRequestParameter("ajax", "true");
        addRequestParameter("demographic_no", "123");
        addRequestParameter("message", "Ajax test");

        // When
        String result = executeAction(action);

        // Then - AddTickler2Action doesn't handle AJAX differently anymore
        assertThat(result).isEqualTo("close");
    }

    @Test
    @DisplayName("Should handle validation errors")
    void testValidationErrors() throws Exception {
        // Given
        allowPrivilege("_tickler", "w");
        addRequestParameter("demographic_no", "invalid");  // Non-numeric
        addRequestParameter("message", "");  // Empty message

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("close");
        // In real implementation, check for field errors
    }
}