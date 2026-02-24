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
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.OpenOWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link MsgDisplayMessages2Action}.
 *
 * <p>Validates security enforcement, session bean creation, and write
 * privilege checks for mutation operations (delete, mark read, mark unread).</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgDisplayMessages2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgDisplayMessages2ActionTest extends OpenOWebTestBase {

    @Mock
    private ProviderManager2 mockProviderManager;

    @Mock
    private MessageListDao mockMessageListDao;

    private MsgDisplayMessages2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(ProviderManager2.class, mockProviderManager);
        replaceSpringUtilsBean(MessageListDao.class, mockMessageListDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        // Set up provider lookup
        Provider testProvider = new Provider();
        testProvider.setProviderNo(TEST_PROVIDER);
        testProvider.setFirstName("Test");
        testProvider.setLastName("Provider");
        when(mockProviderManager.getProvider(any(LoggedInInfo.class), eq(TEST_PROVIDER)))
                .thenReturn(testProvider);

        action = new MsgDisplayMessages2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgDisplayMessages2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
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
    @DisplayName("should throw SecurityException when read privilege is denied")
    void shouldThrowSecurityException_whenReadPrivilegeDenied() {
        // Given
        denyPrivilege("_msg", "r");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should throw SecurityException when write privilege denied for delete")
    void shouldThrowSecurityException_whenWritePrivilegeDeniedForDelete() {
        // Given - read allowed but write denied
        allowPrivilege("_msg", "r");
        denyPrivilege("_msg", "w");
        addRequestParameter("btnDelete", "true");
        action.setMessageNo(new String[]{"100"});

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("write");
    }

    @Test
    @DisplayName("should throw SecurityException when write privilege denied for mark read")
    void shouldThrowSecurityException_whenWritePrivilegeDeniedForMarkRead() {
        // Given
        allowPrivilege("_msg", "r");
        denyPrivilege("_msg", "w");
        addRequestParameter("btnRead", "true");
        action.setMessageNo(new String[]{"100"});

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("write");
    }

    @Test
    @DisplayName("should throw SecurityException when write privilege denied for mark unread")
    void shouldThrowSecurityException_whenWritePrivilegeDeniedForMarkUnread() {
        // Given
        allowPrivilege("_msg", "r");
        denyPrivilege("_msg", "w");
        addRequestParameter("btnUnread", "true");
        action.setMessageNo(new String[]{"100"});

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("write");
    }

    @Test
    @DisplayName("should create new session bean when none exists")
    void shouldCreateNewSessionBean_whenNoneExists() throws Exception {
        // Given - no existing session bean
        allowPrivilege("_msg", "r");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        MsgSessionBean bean = (MsgSessionBean) getMockSession().getAttribute("msgSessionBean");
        assertThat(bean).isNotNull();
        assertThat(bean.getProviderNo()).isEqualTo(TEST_PROVIDER);
        assertThat(bean.getUserName()).isEqualTo("Test Provider");
    }

    @Test
    @DisplayName("should call bulk helper when delete button pressed")
    void shouldCallBulkHelper_whenDeleteButtonPressed() throws Exception {
        // Given
        allowPrivilege("_msg", "r");
        allowPrivilege("_msg", "w");
        addRequestParameter("btnDelete", "true");
        action.setMessageNo(new String[]{"100"});

        MessageList mockMsg = mock(MessageList.class);
        when(mockMessageListDao.findByProviderNoAndMessageNo(TEST_PROVIDER, 100L))
                .thenReturn(List.of(mockMsg));

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockMsg).setDeleted(true);
        verify(mockMessageListDao).merge(mockMsg);
    }

    @Test
    @DisplayName("should call bulk helper when read button pressed")
    void shouldCallBulkHelper_whenReadButtonPressed() throws Exception {
        // Given
        allowPrivilege("_msg", "r");
        allowPrivilege("_msg", "w");
        addRequestParameter("btnRead", "true");
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

    @Test
    @DisplayName("should call bulk helper when unread button pressed")
    void shouldCallBulkHelper_whenUnreadButtonPressed() throws Exception {
        // Given
        allowPrivilege("_msg", "r");
        allowPrivilege("_msg", "w");
        addRequestParameter("btnUnread", "true");
        action.setMessageNo(new String[]{"100"});

        MessageList mockMsg = mock(MessageList.class);
        when(mockMessageListDao.findByProviderNoAndMessageNo(TEST_PROVIDER, 100L))
                .thenReturn(List.of(mockMsg));

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockMsg).setStatus(MessageList.STATUS_NEW);
        verify(mockMessageListDao).merge(mockMsg);
    }

    @Test
    @DisplayName("should return success when no button pressed")
    void shouldReturnSuccess_whenNoButtonPressed() throws Exception {
        // Given - no button parameters set (initial page load)
        allowPrivilege("_msg", "r");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockMessageListDao, never()).findByProviderNoAndMessageNo(anyString(), anyLong());
    }
}
