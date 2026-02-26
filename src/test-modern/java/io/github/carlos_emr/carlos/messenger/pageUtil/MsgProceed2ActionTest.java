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

import io.github.carlos_emr.carlos.commn.dao.RemoteAttachmentsDao;
import io.github.carlos_emr.carlos.commn.model.RemoteAttachments;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MsgProceed2Action}.
 *
 * <p>Validates security enforcement, session bean ownership checks,
 * ID validation, and remote attachment creation/duplicate detection.</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgProceed2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgProceed2ActionTest extends CarlosWebTestBase {

    @Mock
    private RemoteAttachmentsDao mockRemoteAttachmentsDao;

    private MsgProceed2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(RemoteAttachmentsDao.class, mockRemoteAttachmentsDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new MsgProceed2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgProceed2Action.class.getDeclaredField(fieldName);
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
    @DisplayName("should return error when demoId is zero or invalid")
    void shouldReturnError_whenDemoIdIsZeroOrInvalid() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setDemoId("invalid");
        action.setId("100");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("error");
    }

    @Test
    @DisplayName("should return error when message id is zero or invalid")
    void shouldReturnError_whenMessageIdIsZeroOrInvalid() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setDemoId("123");
        action.setId("invalid");

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("error");
    }

    @Test
    @DisplayName("should set confMessage to one when attachment already exists")
    void shouldSetConfMessageToOne_whenAttachmentAlreadyExists() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setDemoId("123");
        action.setId("456");

        RemoteAttachments existing = new RemoteAttachments();
        when(mockRemoteAttachmentsDao.findByDemoNoAndMessageId(123, 456))
                .thenReturn(List.of(existing));

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(getMockRequest().getAttribute("confMessage")).isEqualTo("1");
        verify(mockRemoteAttachmentsDao, never()).persist(any());
    }

    @Test
    @DisplayName("should set confMessage to two when new attachment created")
    void shouldSetConfMessageToTwo_whenNewAttachmentCreated() throws Exception {
        // Given
        allowPrivilege("_msg", "w");
        setUpValidSession();
        action.setDemoId("123");
        action.setId("456");

        when(mockRemoteAttachmentsDao.findByDemoNoAndMessageId(123, 456))
                .thenReturn(Collections.emptyList());

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(getMockRequest().getAttribute("confMessage")).isEqualTo("2");
        verify(mockRemoteAttachmentsDao).persist(any(RemoteAttachments.class));
    }
}
