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

import java.util.List;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EctDefineNewMeasurementGroup2Action}: the Define step checks the new
 * group's name but writes nothing (issue #3335).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("EctDefineNewMeasurementGroup2Action")
class EctDefineNewMeasurementGroup2ActionUnitTest {

    private static final String GROUP = "FAKE Synthetic vitals";

    private SecurityInfoManager security;
    private MeasurementGroupCreationService creation;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private EctDefineNewMeasurementGroup2Action action;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        creation = mock(MeasurementGroupCreationService.class);
        login = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/DefineNewMeasurementGroup");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);

        action = spy(new EctDefineNewMeasurementGroup2Action(security, creation));
        doReturn("invalid").when(action).getText(eq("errors.invalid"), any(String[].class));
        doReturn("duplicate").when(action).getText(eq("error.encounter.addNewMeasurementGroup.duplicateGroupName"), any(String[].class));
        action.setGroupName(GROUP);
        action.setStyleSheet("4");
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseTheRequestBeforeAnyCheck_whenNotPosted(String method) throws Exception {
        request.setMethod(method);

        assertThat(action.execute()).isEqualTo(EctDefineNewMeasurementGroup2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(creation, security);
    }

    @Test
    void shouldContinueToTypeSelectionWithoutWriting_whenTheNameIsNew() throws Exception {
        assertThat(action.execute()).isEqualTo("continue");

        verify(creation).groupExists(GROUP);
        verify(creation, never()).create(anyString(), anyInt(), any());
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo(GROUP);
        assertThat(request.getAttribute("styleSheet")).isEqualTo(4);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc"})
    void shouldCarryNoStyleSheet_whenNoneOrAnUnknownValueIsChosen(String styleSheet) throws Exception {
        action.setStyleSheet(styleSheet);

        assertThat(action.execute()).isEqualTo("continue");
        assertThat(request.getAttribute("styleSheet")).isEqualTo(0);
    }

    @Test
    void shouldReportTheDuplicate_whenTheNameIsAlreadyDefined() throws Exception {
        when(creation.groupExists(GROUP)).thenReturn(true);

        assertThat(action.execute()).isEqualTo("error");
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of("duplicate"));
        assertThat(request.getSession().getAttribute("groupName")).isNull();
        verify(creation, never()).create(anyString(), anyInt(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Tab\tname", "Vitals-BP", "<b>x</b>"})
    void shouldReportAnInvalidName_beforeLookingItUp(String name) throws Exception {
        action.setGroupName(name);

        assertThat(action.execute()).isEqualTo("error");
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of("invalid"));
        verifyNoInteractions(creation);
        assertThat(request.getSession().getAttribute("groupName")).isNull();
    }

    @Test
    void shouldHtmlEncodeTheSubmittedName_inTheErrorMessage() throws Exception {
        action.setGroupName("<b>x</b>");

        action.execute();

        verify(action).getText("errors.invalid", new String[]{"&lt;b&gt;x&lt;/b&gt;"});
    }

    @Test
    void shouldDenyTheRequest_withoutEitherAdministratorPrivilege() {
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.measurements)");
        verifyNoInteractions(creation);
    }
}
