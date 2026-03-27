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

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MsgReDisplayMessages2Action}.
 *
 * <p>Validates security enforcement, session bean ownership checks,
 * redirect on null session bean, empty message array handling,
 * and delegation to {@link MsgBulkOperationHelper}.</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgReDisplayMessages2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgReDisplayMessages2ActionTest extends CarlosWebTestBase {

    @Mock
    private MessageListDao mockMessageListDao;

    private MsgReDisplayMessages2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessageListDao.class, mockMessageListDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new MsgReDisplayMessages2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgReDisplayMessages2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    private void setUpValidSession() {
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        bean.setUserName("Test Provider");
        setSessionAttribute("msgSessionBean", bean);
    }

    @Test
    @DisplayName("should throw SecurityException when no session exists")
    void shouldThrowSecurityException_whenNoSession() {
        // Given
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
    @DisplayName("should throw SecurityException when session bean owner mismatch")
    void shouldThrowSecurityException_whenSessionBeanOwnerMismatch() {
        // Given
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

    @Test
    @DisplayName("should redirect to login when session bean is null")
    void shouldRedirectToLogin_whenSessionBeanIsNull() throws Exception {
        // Given - no session bean in session
        allowPrivilege("_msg", "w");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isNull();
        assertThat(getMockResponse().getRedirectedUrl()).contains("index.jsp");
    }

    @Test
    @DisplayName("should return success when messageNo is empty")
    void shouldReturnSuccess_whenMessageNoIsEmpty() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setMessageNo(new String[]{});

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockMessageListDao, never()).findByProviderNoAndMessageNo(anyString(), anyLong());
    }

    @Test
    @DisplayName("should delegate to bulk helper with status read")
    void shouldDelegateToBulkHelper_withStatusRead() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setMessageNo(new String[]{"100"});

        MessageList mockMsg = mock(MessageList.class);
        when(mockMessageListDao.findByProviderNoAndMessageNo(TEST_PROVIDER, 100L))
                .thenReturn(List.of(mockMsg));

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockMsg).setStatus(MessageList.STATUS_READ);
        verify(mockMessageListDao).merge(mockMsg);
    }
}
