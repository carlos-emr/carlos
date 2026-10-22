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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MeasurementGraphAction22Action} logout redirect short-circuiting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeasurementGraphAction22Action logout redirect")
@Tag("unit")
@Tag("clinical")
class MeasurementGraphAction22ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return NONE when graph session is unauthenticated")
    void shouldReturnNone_whenGraphSessionUnauthenticated() throws Exception {
        MeasurementGraphAction22Action action = new MeasurementGraphAction22Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
        assertThat(response.getContentType()).isNull();
    }
}
