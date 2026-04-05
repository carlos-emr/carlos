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

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link DemographicLinkMsg2Action}.
 *
 * @since 2026-04-04
 */
@DisplayName("DemographicLinkMsg2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicLinkMsg2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private DemographicLinkMsg2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicLinkMsg2Action();

        java.lang.reflect.Field secField = DemographicLinkMsg2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
    }

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
    @DisplayName("should return SUCCESS when user has demographic read privilege")
    void shouldReturnSuccess_whenUserHasReadPrivilege() throws Exception {
        allowPrivilege("_demographic", "r");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }
}
