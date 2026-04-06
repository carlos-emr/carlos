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
package io.github.carlos_emr.carlos.www;

import io.github.carlos_emr.carlos.commn.dao.SystemMessageDao;
import io.github.carlos_emr.carlos.commn.model.SystemMessage;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SystemMessage2Action} focusing on trust boundary enforcement:
 * session attributes must be populated from DB-validated data, not raw request parameters.
 *
 * @since 2026-04-06
 */
@DisplayName("SystemMessage2Action Tests")
@Tag("integration")
@Tag("admin")
class SystemMessage2ActionTest extends CarlosWebTestBase {

    @Mock
    private SystemMessageDao mockSystemMessageDao;

    private SystemMessage2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(SystemMessageDao.class, mockSystemMessageDao);

        action = new SystemMessage2Action();
        injectField("systemMessageDao", mockSystemMessageDao);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = SystemMessage2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Nested
    @DisplayName("edit()")
    class EditMethod {

        @Test
        @DisplayName("should store DB-derived ID in session when message exists")
        void shouldStoreDbDerivedId_whenMessageExists() throws Exception {
            // Given
            SystemMessage msg = new SystemMessage();
            msg.setId(42);
            when(mockSystemMessageDao.find(42)).thenReturn(msg);
            addRequestParameter("id", "42");

            // When
            String result = executeActionMethod(action, "edit");

            // Then - session must contain the DB entity's ID, not the raw request string
            assertThat(result).isEqualTo("edit");
            assertThat(getMockSession().getAttribute("systemMessageId")).isEqualTo("42");
            verify(mockSystemMessageDao).find(42);
        }

        @Test
        @DisplayName("should redirect to list when message ID does not exist in DB")
        void shouldRedirectToList_whenMessageIdNotFoundInDb() throws Exception {
            // Given - ID is syntactically valid but does not exist
            when(mockSystemMessageDao.find(anyInt())).thenReturn(null);
            when(mockSystemMessageDao.findAll()).thenReturn(java.util.Collections.emptyList());
            addRequestParameter("id", "999");

            // When
            String result = executeActionMethod(action, "edit");

            // Then - must NOT store the untrusted ID in session
            assertThat(result).isEqualTo("list");
            assertThat(getMockSession().getAttribute("systemMessageId")).isNull();
        }

        @Test
        @DisplayName("should store empty string in session when no ID parameter is provided")
        void shouldStoreEmptyString_whenNoIdParameter() throws Exception {
            // Given - no "id" parameter

            // When
            String result = executeActionMethod(action, "edit");

            // Then
            assertThat(result).isEqualTo("edit");
            assertThat(getMockSession().getAttribute("systemMessageId")).isEqualTo("");
        }
    }
}
