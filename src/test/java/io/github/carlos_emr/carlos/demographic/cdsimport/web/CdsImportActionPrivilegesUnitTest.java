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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsWeb;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Action gates must remain effective when the finer application-service gate is mocked. */
class CdsImportActionPrivilegesUnitTest extends CarlosUnitTestBase {
    static Stream<Arguments> actions() {
        return Stream.of(
                Arguments.of(CdsUpload2Action.class, "POST", true),
                Arguments.of(CdsImportControl2Action.class, "POST", true),
                Arguments.of(SubmitCdsImport2Action.class, "POST", true),
                Arguments.of(CdsImportStatus2Action.class, "GET", true),
                Arguments.of(CdsImportDiagnostic2Action.class, "GET", true),
                Arguments.of(CdsImportReport2Action.class, "GET", true),
                Arguments.of(ViewCdsImport2Action.class, "GET", true),
                Arguments.of(LegacyCdsImportLog2Action.class, "GET", true),
                Arguments.of(ViewCdsSourceFields2Action.class, "GET", false),
                Arguments.of(CdsSourceFieldContent2Action.class, "GET", false));
    }

    @AfterEach void clearContext() { ActionContext.clear(); }

    static Stream<Arguments> operatorGrants() {
        return actions().filter(row -> (Boolean) row.get()[2]).flatMap(row -> Stream.of(
                Arguments.of(row.get()[0], row.get()[1], "_admin"),
                Arguments.of(row.get()[0], row.get()[1], "_cds_import")));
    }

    @ParameterizedTest @MethodSource("operatorGrants")
    void shouldAcceptEitherOperatorGrant_andRetainFinerAuthorization(
            Class<? extends ActionSupport> type, String method, String grant) throws Exception {
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_demographic"), eq("w"), (String) isNull())).thenReturn(true);
        when(security.hasPrivilege(any(), eq(grant), eq("w"), (String) isNull())).thenReturn(true);
        List<Object> services = new ArrayList<>();
        ActionSupport action = action(type, method, security, services);
        action.execute();
        var access = services.stream()
                .filter(io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService.class::isInstance)
                .map(io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService.class::cast)
                .findFirst().orElseThrow();
        org.mockito.Mockito.verify(access).requireOperatorSession(any());
    }

    @ParameterizedTest @MethodSource("actions")
    void shouldDenyMissingDemographicPrivilege_beforeCallingAnyService(
            Class<? extends ActionSupport> type, String method, boolean operator) throws Exception {
        var security = mock(SecurityInfoManager.class);
        List<Object> services = new ArrayList<>();
        ActionSupport action = action(type, method, security, services);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");
        verifyNoInteractions(services.toArray());
    }

    @ParameterizedTest @MethodSource("actions")
    void shouldRequireOperatorPrivilege_onlyForImportOperations(
            Class<? extends ActionSupport> type, String method, boolean operator) throws Exception {
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_demographic"), eq(operator ? "w" : "r"), (String) isNull()))
                .thenReturn(true);
        List<Object> services = new ArrayList<>();
        ActionSupport action = action(type, method, security, services);
        if (operator) {
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_cds_import)");
            verifyNoInteractions(services.toArray());
        } else {
            // Source-field routes use the chart service's patient/category gate, not import-operator access.
            action.execute();
            org.mockito.Mockito.verify(security).hasPrivilege(any(), eq("_demographic"), eq("r"), (String) isNull());
            org.mockito.Mockito.verifyNoMoreInteractions(security);
        }
    }

    private ActionSupport action(Class<? extends ActionSupport> type, String method,
            SecurityInfoManager security, List<Object> services) throws Exception {
        var request = new MockHttpServletRequest(); request.setMethod(method);
        request.addParameter(CdsWeb.API_VERSION_PARAMETER, Integer.toString(CdsWeb.API_VERSION));
        var actor = mock(LoggedInInfo.class);
        var login = new Security(); login.setSecurityNo(42);
        when(actor.getLoggedInSecurity()).thenReturn(login);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", actor);
        ActionContext.of().withServletRequest(request).withServletResponse(new MockHttpServletResponse()).bind();
        var constructor = type.getConstructors()[0];
        List<Object> arguments = new ArrayList<>();
        for (Class<?> dependency : constructor.getParameterTypes()) {
            if (dependency == SecurityInfoManager.class) arguments.add(security);
            else { Object service = mock(dependency); services.add(service); arguments.add(service); }
        }
        return (ActionSupport) constructor.newInstance(arguments.toArray());
    }
}
