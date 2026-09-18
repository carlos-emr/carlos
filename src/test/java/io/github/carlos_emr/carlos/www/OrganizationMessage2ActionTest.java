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

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityMessageDao;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.services.OrganizationMessageManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OrganizationMessage2Action}'s authorization split.
 *
 * <p>The facility-message banner on the provider schedule is a read-only broadcast: it must
 * render for any authenticated clinician. The management methods behind the same action
 * (list/edit/save) stay behind {@code _admin} write. See issue #3728.
 *
 * @since 2026-09-18
 */
@DisplayName("OrganizationMessage2Action Tests")
@Tag("unit")
@Tag("admin")
class OrganizationMessage2ActionTest extends CarlosWebTestBase {

    @Mock
    private OrganizationMessageManager mockOrganizationMessageManager;

    @Mock
    private FacilityDao mockFacilityDao;

    @Mock
    private FacilityMessageDao mockFacilityMessageDao;

    @Mock
    private ProgramManager mockProgramManager;

    @Mock
    private ProgramManager2 mockProgramManager2;

    private OrganizationMessage2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(OrganizationMessageManager.class, mockOrganizationMessageManager);
        replaceSpringUtilsBean(FacilityDao.class, mockFacilityDao);
        replaceSpringUtilsBean(FacilityMessageDao.class, mockFacilityMessageDao);
        replaceSpringUtilsBean(ProgramManager.class, mockProgramManager);
        replaceSpringUtilsBean(ProgramManager2.class, mockProgramManager2);

        action = new OrganizationMessage2Action();
        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("mgr", mockOrganizationMessageManager);
        injectField("facilityDao", mockFacilityDao);
        injectField("facilityMessageDao", mockFacilityMessageDao);
        injectField("programManager", mockProgramManager);
        injectField("programManager2", mockProgramManager2);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = OrganizationMessage2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Nested
    @DisplayName("execute() - Security")
    class SecurityChecks {

        @Test
        @DisplayName("should render the facility banner when view is requested without admin write")
        void shouldRenderFacilityBanner_whenViewRequestedWithoutAdminWrite() throws Exception {
            // Given - a clinician with no admin rights loads the provider schedule (issue #3728)
            denyPrivilege("_admin", "w");
            denyPrivilege("_admin", "r");
            when(mockProgramManager2.getCurrentProgramInDomain(any(LoggedInInfo.class), any()))
                .thenReturn(null);
            when(mockFacilityMessageDao.getMessagesByFacilityIdOrNullAndProgramIdOrNull(null, null))
                .thenReturn(java.util.Collections.emptyList());
            addRequestParameter("method", "view");

            // When
            String result = executeAction(action);

            // Then - the banner renders and no privilege check rejects the read
            assertThat(result).isEqualTo("view");
            verify(mockFacilityMessageDao).getMessagesByFacilityIdOrNullAndProgramIdOrNull(null, null);
            verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw SecurityException when view is requested without a logged-in session")
        void shouldThrowSecurityException_whenViewRequestedWithoutSession() throws Exception {
            // Given - no LoggedInInfo in session (LoginFilter would normally have rejected this)
            getMockSession().removeAttribute(new LoggedInInfo().LOGGED_IN_INFO_KEY);
            addRequestParameter("method", "view");

            // When/Then
            assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not logged in");
            verifyNoInteractions(mockFacilityMessageDao);
        }

        @Test
        @DisplayName("should throw SecurityException when save is requested without admin write")
        void shouldThrowSecurityException_whenSaveRequestedWithoutAdminWrite() throws Exception {
            // Given - the management methods stay behind _admin write
            denyPrivilege("_admin", "w");
            addRequestParameter("method", "save");

            // When/Then - denial fires before any persistence happens
            assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_admin)");
            verifyNoInteractions(mockOrganizationMessageManager);
        }

        @Test
        @DisplayName("should throw SecurityException when the admin list is requested without admin write")
        void shouldThrowSecurityException_whenListRequestedWithoutAdminWrite() throws Exception {
            // Given - no method parameter routes to the administrative list
            denyPrivilege("_admin", "w");

            // When/Then
            assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_admin)");
            verifyNoInteractions(mockOrganizationMessageManager);
        }
    }
}
