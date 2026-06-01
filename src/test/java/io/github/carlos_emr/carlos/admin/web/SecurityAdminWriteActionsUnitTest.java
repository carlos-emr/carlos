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
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.http.HttpServletResponse;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for security-admin mutator actions that must require
 * admin write privilege.
 *
 * @since 2026-05-31
 */
@DisplayName("Security admin write 2Actions")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityAdminWriteActionsUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CarlosMethodSecurity methodSecurity;

    @BeforeEach
    void setUpServletActionContext() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        methodSecurity = mock(CarlosMethodSecurity.class);
        registerMock(CarlosMethodSecurity.class, methodSecurity);
        servletActionContextMock = org.mockito.Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDownServletActionContext() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    static Stream<Arguments> writeActions() {
        return Stream.of(
            Arguments.of("SecurityAddSecurity2Action",
                    (Function<CarlosMethodSecurity, ActionSupport>) SecurityAddSecurity2Action::new),
            Arguments.of("SecurityUpdate2Action",
                    (Function<CarlosMethodSecurity, ActionSupport>) SecurityUpdate2Action::new)
        );
    }

    static Stream<Arguments> springWiredActions() {
        return Stream.of(
            Arguments.of("SecurityAddSecurity2Action",
                    (Supplier<ActionSupport>) SecurityAddSecurity2Action::new),
            Arguments.of("SecurityUpdate2Action",
                    (Supplier<ActionSupport>) SecurityUpdate2Action::new)
        );
    }

    @ParameterizedTest(name = "{0} denies POST without admin write privilege")
    @MethodSource("writeActions")
    @DisplayName("should require admin write privilege for POST")
    void shouldRequireAdminWritePrivilege_forPost(
            String actionName, Function<CarlosMethodSecurity, ActionSupport> actionFactory) {
        when(methodSecurity.hasAdminWrite()).thenReturn(false);
        request.setMethod("POST");
        ActionSupport action = actionFactory.apply(methodSecurity);

        assertThatThrownBy(action::execute)
                .as("%s should reject POST without admin write privilege", actionName)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("(_admin or _admin.userAdmin)");
        verify(methodSecurity).hasAdminWrite();
    }

    @ParameterizedTest(name = "{0} allows POST with admin write privilege")
    @MethodSource("writeActions")
    @DisplayName("should return success for POST after passing admin write privilege")
    void shouldReturnSuccess_forPostWithAdminWritePrivilege(
            String actionName, Function<CarlosMethodSecurity, ActionSupport> actionFactory) throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        request.setMethod("POST");

        String result = actionFactory.apply(methodSecurity).execute();

        assertThat(result)
                .as("%s should allow POST after admin write privilege is granted", actionName)
                .isEqualTo(ActionSupport.SUCCESS);
        verify(methodSecurity).hasAdminWrite();
    }

    @ParameterizedTest(name = "{0} resolves CarlosMethodSecurity from SpringUtils")
    @MethodSource("springWiredActions")
    @DisplayName("should use default constructor Spring wiring")
    void shouldUseSpringWiring_forDefaultConstructor(
            String actionName, Supplier<ActionSupport> actionFactory) throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        request.setMethod("POST");

        String result = actionFactory.get().execute();

        assertThat(result)
                .as("%s should use the registered CarlosMethodSecurity bean", actionName)
                .isEqualTo(ActionSupport.SUCCESS);
        verify(methodSecurity).hasAdminWrite();
    }

    @ParameterizedTest(name = "{0} rejects GET with 405 when admin write is granted")
    @MethodSource("writeActions")
    @DisplayName("should reject GET after passing admin write privilege")
    void shouldRejectGet_whenAdminWritePrivilegeGranted(
            String actionName, Function<CarlosMethodSecurity, ActionSupport> actionFactory) throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        request.setMethod("GET");

        String result = actionFactory.apply(methodSecurity).execute();

        assertThat(result)
                .as("%s should not render a result view for rejected GET", actionName)
                .isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus())
                .as("%s should send 405 for GET", actionName)
                .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(methodSecurity).hasAdminWrite();
    }
}
