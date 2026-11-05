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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The read-only gates of the BC billing administration pages (#4767): each refuses a request without
 * a session or without either of its two conditions, forwards with them, and the gates that take an id
 * reject a malformed one only after the rights checks.
 */
@DisplayName("BC billing administration page gates")
@Tag("unit")
@Tag("billing")
class BcBillingAdminPageGatesUnitTest extends CarlosUnitTestBase {

    /** One gate: how to build it, and its two conditions as {right, objects...} (any object satisfies). */
    private record Gate(String name, Function<SecurityInfoManager, ActionSupport> factory,
                        String[] first, String[] second, String idParameter) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final String[] BILLING_READ = {"r", "_billing"};
    private static final String[] ADMIN_BILLING_WRITE = {"w", "_admin.billing", "_admin"};

    static Stream<Gate> gates() {
        return Stream.of(
                new Gate("ViewBillingCodeAdjust", ViewBillingCodeAdjust2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, null),
                new Gate("ViewBillingPrivateCodeAdjust", ViewBillingPrivateCodeAdjust2Action::new, BILLING_READ,
                        new String[]{"r", "_admin.billing", "_admin"}, null),
                new Gate("ViewBillingAddCode", ViewBillingAddCode2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, null),
                new Gate("ViewBillingAddPrivateCode", ViewBillingAddPrivateCode2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, null),
                new Gate("ViewBillingEditCode", ViewBillingEditCode2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, "codeId"),
                new Gate("ViewBillingManageReferralDoc", ViewBillingManageReferralDoc2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, null),
                new Gate("ViewBillingAddReferralDoc", ViewBillingAddReferralDoc2Action::new, BILLING_READ, ADMIN_BILLING_WRITE, "id"),
                new Gate("ViewReconcileReports", ViewReconcileReports2Action::new, new String[]{"r", "_admin.billing"},
                        new String[]{"r", "_report", "_admin.reporting", "_admin"}, null));
    }

    static Stream<Gate> writeConditionGates() {
        return gates().filter(gate -> "w".equals(gate.second()[0]));
    }

    static Stream<Gate> idGates() {
        return gates().filter(gate -> gate.idParameter() != null);
    }

    /** Every (gate, condition, alternative object) whose grant alone satisfies that condition. */
    static Stream<Arguments> alternatives() {
        return gates().flatMap(gate -> Stream.of(gate.first(), gate.second()).flatMap(condition ->
                List.of(condition).subList(1, condition.length).stream()
                        .map(object -> Arguments.of(gate, condition == gate.first(), object))));
    }

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final Set<String> granted = new HashSet<>();

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/billing/CA/BC/gate");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull()))
                .thenAnswer(call -> granted.contains(call.getArgument(1) + ":" + call.getArgument(2)));
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void grant(String[] condition, String object) {
        granted.add(object + ":" + condition[0]);
    }

    private void grantBoth(Gate gate) {
        grant(gate.first(), gate.first()[1]);
        grant(gate.second(), gate.second()[1]);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gates")
    @DisplayName("should refuse a request without a session, before any rights lookup")
    void shouldRefuse_withoutSession(Gate gate) {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> gate.factory().apply(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class).hasMessage("missing session");
        verify(securityInfoManager, never()).hasPrivilege(any(), anyString(), anyString(), isNull());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gates")
    @DisplayName("should forward to its page when both conditions are held")
    void shouldForward_withBothConditions(Gate gate) throws Exception {
        grantBoth(gate);

        assertThat(gate.factory().apply(securityInfoManager).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gates")
    @DisplayName("should refuse without its first condition, even with the second")
    void shouldRefuse_withoutFirstCondition(Gate gate) {
        grant(gate.second(), gate.second()[1]);

        assertThatThrownBy(() -> gate.factory().apply(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class).hasMessageStartingWith("missing required sec object (");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gates")
    @DisplayName("should refuse without its second condition, even with the first")
    void shouldRefuse_withoutSecondCondition(Gate gate) {
        grant(gate.first(), gate.first()[1]);

        assertThatThrownBy(() -> gate.factory().apply(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class).hasMessageStartingWith("missing required sec object (");
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("alternatives")
    @DisplayName("should accept any one alternative object of a condition")
    void shouldAccept_withAnyAlternative(Gate gate, boolean firstCondition, String object) throws Exception {
        String[] condition = firstCondition ? gate.first() : gate.second();
        String[] other = firstCondition ? gate.second() : gate.first();
        grant(condition, object);
        grant(other, other[1]);

        assertThat(gate.factory().apply(securityInfoManager).execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writeConditionGates")
    @DisplayName("should not accept read where its page asks for write")
    void shouldRefuse_withReadWhereWriteIsRequired(Gate gate) {
        grant(gate.first(), gate.first()[1]);
        granted.add(gate.second()[1] + ":r");

        assertThatThrownBy(() -> gate.factory().apply(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class).hasMessageStartingWith("missing required sec object (");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idGates")
    @DisplayName("should answer 400 to a malformed id, and only after the rights checks")
    void shouldRejectMalformedId_afterRights(Gate gate) throws Exception {
        request.setParameter(gate.idParameter(), "12;x");

        assertThatThrownBy(() -> gate.factory().apply(securityInfoManager).execute())
                .isInstanceOf(SecurityException.class);

        grantBoth(gate);
        assertThat(gate.factory().apply(securityInfoManager).execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idGates")
    @DisplayName("should forward with a numeric id it does not look up, and without one")
    void shouldForward_withNumericOrMissingId(Gate gate) throws Exception {
        grantBoth(gate);

        assertThat(gate.factory().apply(securityInfoManager).execute()).isEqualTo(ActionSupport.SUCCESS);
        request.setParameter(gate.idParameter(), "987654321");
        assertThat(gate.factory().apply(securityInfoManager).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
