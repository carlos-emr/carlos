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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link ProviderProperty2Action#save()} and
 * {@link ProviderProperty2Action#OscarMsgRecvd()}.
 *
 * <p>Validates the fix for Issue #3 (missing null session check in save())
 * and Issue #9 (null value parameter in OscarMsgRecvd).</p>
 *
 * @since 2026-02-20
 */
@DisplayName("ProviderProperty2Action Save/OscarMsgRecvd Tests")
@Tag("integration")
@Tag("provider")
class ProviderProperty2ActionSaveTest extends CarlosWebTestBase {

    @Mock
    private UserPropertyDAO mockUserPropertyDAO;

    @Mock
    private ProviderManager2 mockProviderManager;

    private ProviderProperty2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(UserPropertyDAO.class, mockUserPropertyDAO);
        replaceSpringUtilsBean(ProviderManager2.class, mockProviderManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new ProviderProperty2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("userPropertyDAO", mockUserPropertyDAO);
        injectField("providerManager2", mockProviderManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = ProviderProperty2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when save() called without session")
    void shouldThrowSecurityException_whenSaveCalledWithoutSession() {
        // Given - remove logged-in info
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, null);

        // When/Then - SecurityException wrapped in InvocationTargetException by reflection
        assertThatThrownBy(() -> executeActionMethod(action, "save"))
                .hasCauseInstanceOf(SecurityException.class)
                .hasRootCauseMessage("No valid session found");
    }

    @Test
    @DisplayName("should save properties when session is valid")
    void shouldSaveProperties_whenSessionIsValid() throws Exception {
        // Given
        addRequestParameter("dateProperty.value", "A");
        addRequestParameter("singleViewProperty.value", "yes");

        when(mockUserPropertyDAO.getProp(TEST_PROVIDER, UserProperty.STALE_NOTEDATE))
                .thenReturn(null);
        when(mockUserPropertyDAO.getProp(TEST_PROVIDER, UserProperty.STALE_FORMAT))
                .thenReturn(null);

        // When
        String result = executeActionMethod(action, "save");

        // Then
        assertThat(result).isEqualTo("success");
        verify(mockUserPropertyDAO, times(2)).saveProp(any(UserProperty.class));
    }

    @Test
    @DisplayName("should update existing property when prop already exists")
    void shouldUpdateExistingProperty_whenPropAlreadyExists() throws Exception {
        // Given - existing properties
        addRequestParameter("dateProperty.value", "-7");
        addRequestParameter("singleViewProperty.value", "no");

        UserProperty existingDate = new UserProperty();
        existingDate.setId(1);
        existingDate.setProviderNo(TEST_PROVIDER);
        existingDate.setName(UserProperty.STALE_NOTEDATE);
        existingDate.setValue("A");

        UserProperty existingFormat = new UserProperty();
        existingFormat.setId(2);
        existingFormat.setProviderNo(TEST_PROVIDER);
        existingFormat.setName(UserProperty.STALE_FORMAT);
        existingFormat.setValue("yes");

        when(mockUserPropertyDAO.getProp(TEST_PROVIDER, UserProperty.STALE_NOTEDATE))
                .thenReturn(existingDate);
        when(mockUserPropertyDAO.getProp(TEST_PROVIDER, UserProperty.STALE_FORMAT))
                .thenReturn(existingFormat);

        // When
        String result = executeActionMethod(action, "save");

        // Then - existing props should be updated (not new ones created)
        assertThat(result).isEqualTo("success");
        assertThat(existingDate.getValue()).isEqualTo("-7");
        assertThat(existingFormat.getValue()).isEqualTo("no");
        verify(mockUserPropertyDAO).saveProp(existingDate);
        verify(mockUserPropertyDAO).saveProp(existingFormat);
    }

    @Test
    @DisplayName("should not save prop when OscarMsgRecvd value is null")
    void shouldNotSaveProp_whenOscarMsgRecvdValueIsNull() throws Exception {
        // Given - no "value" parameter (null)

        // When
        String result = executeActionMethod(action, "OscarMsgRecvd");

        // Then - should not call saveProp since value is null
        assertThat(result).isNull();
        verify(mockUserPropertyDAO, never()).saveProp(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should save prop when OscarMsgRecvd value is provided")
    void shouldSaveProp_whenOscarMsgRecvdValueIsProvided() throws Exception {
        // Given - value must be in H:m format (e.g., "9:0", "14:30")
        addRequestParameter("value", "9:0");

        // When
        String result = executeActionMethod(action, "OscarMsgRecvd");

        // Then
        assertThat(result).isNull();
        verify(mockUserPropertyDAO).saveProp(TEST_PROVIDER, UserProperty.OSCAR_MSG_RECVD, "9:0");
    }

    @Test
    @DisplayName("should not save prop when OscarMsgRecvd value has invalid format")
    void shouldNotSaveProp_whenOscarMsgRecvdValueHasInvalidFormat() throws Exception {
        // Given - value is not in H:m format
        addRequestParameter("value", "yes");

        // When
        String result = executeActionMethod(action, "OscarMsgRecvd");

        // Then - should not call saveProp since value format is invalid
        assertThat(result).isNull();
        verify(mockUserPropertyDAO, never()).saveProp(anyString(), anyString(), anyString());
    }
}
