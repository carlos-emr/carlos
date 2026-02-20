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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.OpenOWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link MsgCreateMessage2Action}.
 *
 * <p>Validates security enforcement, session validation, and the fix for
 * ConversionUtils dead null checks (Issue #1).</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgCreateMessage2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgCreateMessage2ActionTest extends OpenOWebTestBase {

    @Mock
    private MessengerDemographicManager mockDemoManager;

    private MsgCreateMessage2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessengerDemographicManager.class, mockDemoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new MsgCreateMessage2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("messengerDemographicManager", mockDemoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgCreateMessage2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when no session exists")
    void shouldThrowSecurityException_whenNoSessionExists() {
        // Given - remove logged-in info from session
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, null);

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No valid session");
    }

    @Test
    @DisplayName("should throw SecurityException when msg write privilege is denied")
    void shouldThrowSecurityException_whenMsgWritePrivilegeDenied() {
        // Given
        denyPrivilege("_msg", "w");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should throw SecurityException when session bean is missing")
    void shouldThrowSecurityException_whenSessionBeanMissing() {
        // Given - no msgSessionBean in session
        allowPrivilege("_msg", "w");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("session not initialized");
    }

    @Test
    @DisplayName("should throw SecurityException when session bean belongs to different provider")
    void shouldThrowSecurityException_whenSessionBeanBelongsToDifferentProvider() {
        // Given - session bean for a different provider
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo("DIFFERENT_PROVIDER");
        bean.setUserName("Other User");
        setSessionAttribute("msgSessionBean", bean);

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another provider");
    }
}
