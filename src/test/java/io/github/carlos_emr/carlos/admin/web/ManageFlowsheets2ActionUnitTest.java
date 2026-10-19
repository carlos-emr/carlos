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

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Issue #4623: the Administration left nav and {@code manageFlowsheets.jsp} admit {@code _admin},
 * {@code _admin.misc} or {@code _admin.flowsheet} <em>read</em>, but the action used to demand
 * <em>write</em> for every request, so the seeded doctor (holding {@code _admin.flowsheet} read)
 * was offered the link and then refused. Viewing the list is a read; enabling or disabling a
 * flowsheet is a write.
 */
@DisplayName("ManageFlowsheets2Action read/write privilege split")
@Tag("unit")
@Tag("security")
class ManageFlowsheets2ActionUnitTest extends CarlosUnitTestBase {

    private static final String[] GATE_OBJECTS = {"_admin", "_admin.misc", "_admin.flowsheet"};

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<MeasurementTemplateFlowSheetConfig> configStaticMock;
    private MeasurementTemplateFlowSheetConfig mockConfig;
    private SecurityInfoManager mockSecurityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ManageFlowsheets2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
        mockConfig = mock(MeasurementTemplateFlowSheetConfig.class);
        configStaticMock = mockStatic(MeasurementTemplateFlowSheetConfig.class);
        configStaticMock.when(MeasurementTemplateFlowSheetConfig::getInstance).thenReturn(mockConfig);
        mockSecurityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        action = new ManageFlowsheets2Action();
    }

    @AfterEach
    void tearDown() {
        configStaticMock.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    /**
     * Grants exactly one right on exactly one object the way {@code SecurityInfoManagerImpl} does:
     * a stronger right (w, x) satisfies a weaker request (r), never the reverse.
     */
    private void grant(String objectName, String right) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(objectName), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    String requested = invocation.getArgument(2);
                    return "w".equals(right) || right.equals(requested);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("a read-only flowsheet administrator can open the list")
    void shouldRenderList_whenOnlyFlowsheetReadIsGranted(String httpMethod) throws Exception {
        grant("_admin.flowsheet", "r");
        request.setMethod(httpMethod);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("flowsheetWriteAccess")).isEqualTo(Boolean.FALSE);
        verifyNoInteractions(mockConfig);
    }

    @Test
    @DisplayName("each gate object at read opens the list without write controls")
    void shouldRenderReadOnlyList_forEachGateObjectAtRead() throws Exception {
        for (String object : GATE_OBJECTS) {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                    .thenReturn(false);
            grant(object, "r");
            request.removeAttribute("flowsheetWriteAccess");

            assertThat(action.execute()).as("read on %s", object).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("flowsheetWriteAccess")).as("write access via %s", object)
                    .isEqualTo(Boolean.FALSE);
        }
    }

    @Test
    @DisplayName("each gate object at write still opens the list and marks it writable")
    void shouldRenderWritableList_forEachGateObjectAtWrite() throws Exception {
        for (String object : GATE_OBJECTS) {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                    .thenReturn(false);
            grant(object, "w");
            request.removeAttribute("flowsheetWriteAccess");

            assertThat(action.execute()).as("write on %s", object).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("flowsheetWriteAccess")).as("write access via %s", object)
                    .isEqualTo(Boolean.TRUE);
        }
    }

    @Test
    @DisplayName("a login with no flowsheet or admin right is refused the list")
    void shouldThrowSecurityException_whenNoGateObjectIsGranted() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");
        assertThat(request.getAttribute("flowsheetWriteAccess")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"disable", "enable"})
    @DisplayName("a read-only flowsheet administrator cannot change a flowsheet")
    void shouldRefuseMutation_whenOnlyFlowsheetReadIsGranted(String method) {
        grant("_admin.flowsheet", "r");
        request.setMethod("POST");
        request.setParameter("method", method);
        request.setParameter("name", "diabetes");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");

        verifyNoInteractions(mockConfig);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("a flowsheet writer can still disable a flowsheet and is redirected back")
    void shouldDisableFlowsheet_whenFlowsheetWriteIsGranted() throws Exception {
        grant("_admin.flowsheet", "w");
        request.setMethod("POST");
        request.setParameter("method", "disable");
        request.setParameter("name", "diabetes");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        verify(mockConfig).disableFlowsheet("diabetes");
        assertThat(response.getRedirectedUrl()).endsWith("/admin/ManageFlowsheets");
    }

    @Test
    @DisplayName("a flowsheet writer can still enable a flowsheet and is redirected back")
    void shouldEnableFlowsheet_whenFlowsheetWriteIsGranted() throws Exception {
        grant("_admin.flowsheet", "w");
        request.setMethod("POST");
        request.setParameter("method", "enable");
        request.setParameter("name", "diabetes");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        verify(mockConfig).enableFlowsheet("diabetes");
        assertThat(response.getRedirectedUrl()).endsWith("/admin/ManageFlowsheets");
    }

    @Test
    @DisplayName("a GET carrying a mutation method never changes a flowsheet")
    void shouldNotMutate_whenMutationMethodArrivesOnGet() throws Exception {
        grant("_admin.flowsheet", "w");
        request.setMethod("GET");
        request.setParameter("method", "disable");
        request.setParameter("name", "diabetes");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        verifyNoInteractions(mockConfig);
    }

    @Test
    @DisplayName("a POST with no method is a read: a read-only administrator gets the list back")
    void shouldRenderList_whenPostCarriesNoMutationMethodAndOnlyReadIsGranted() throws Exception {
        grant("_admin.flowsheet", "r");
        request.setMethod("POST");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("flowsheetWriteAccess")).isEqualTo(Boolean.FALSE);
        verifyNoInteractions(mockConfig);
    }
}
