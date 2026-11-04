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

import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService.Outcome;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
 * Unit tests for {@link EctAddMeasurementGroup2Action}, the step that creates a new measurement
 * group together with its first types (issue #3335).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("EctAddMeasurementGroup2Action")
class EctAddMeasurementGroup2ActionUnitTest extends CarlosUnitTestBase {

    private static final String GROUP = "FAKE Synthetic vitals";

    private SecurityInfoManager security;
    private MeasurementGroupCreationService creation;
    private MeasurementTypeDao typeDao;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private EctAddMeasurementGroup2Action action;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        creation = mock(MeasurementGroupCreationService.class);
        typeDao = mock(MeasurementTypeDao.class);
        login = mock(LoggedInInfo.class);
        // EctTypeDisplayNameBeanHandler, used to show the page again, reads the types this way.
        registerMock(MeasurementTypeDao.class, typeDao);
        registerMock(MeasurementGroupDao.class, mock(MeasurementGroupDao.class));
        when(typeDao.findUniqueTypeDisplayNames()).thenReturn(List.of("FAKE Weight", "FAKE Height"));

        request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/AddMeasurementGroup");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);

        action = spy(new EctAddMeasurementGroup2Action(security, creation));
        doReturn("no types").when(action).getText(eq("error.encounter.addNewMeasurementGroup.noTypesSelected"), any(String[].class));
        doReturn("duplicate").when(action).getText(eq("error.encounter.addNewMeasurementGroup.duplicateGroupName"), any(String[].class));
        action.setGroupName(GROUP);
        action.setStyleSheet("4");
        action.setForward("add");
        action.setSelectedAddTypes(new String[]{"FAKE Weight", "FAKE Height"});
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

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(creation, security);
    }

    @Test
    void shouldCreateTheGroupWithTheChosenTypes_whenAddIsPosted() throws Exception {
        when(creation.create(eq(GROUP), eq(4), any())).thenReturn(Outcome.CREATED);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.SUCCESS);
        verify(creation).create(GROUP, 4, List.of("FAKE Weight", "FAKE Height"));
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo(GROUP);
    }

    @Test
    void shouldShowThePageAgainWithTheReason_whenNoTypeIsChosen() throws Exception {
        action.setSelectedAddTypes(null);
        when(creation.create(eq(GROUP), eq(4), any())).thenReturn(Outcome.NO_TYPES);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.INPUT);
        verify(creation).create(GROUP, 4, List.of());
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of("no types"));
        assertThat(request.getAttribute("styleSheet")).isEqualTo(4);
        assertThat((Collection<?>) request.getSession().getAttribute("allTypeDisplayNames")).hasSize(2);
    }

    @Test
    void shouldShowThePageAgainWithTheReason_whenTheNameWasTakenMeanwhile() throws Exception {
        when(creation.create(eq(GROUP), eq(4), any())).thenReturn(Outcome.DUPLICATE_NAME);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.INPUT);
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.of("duplicate"));
    }

    @Test
    void shouldNameTheGroup_inTheErrorMessage() throws Exception {
        when(creation.create(eq(GROUP), eq(4), any())).thenReturn(Outcome.NO_TYPES);

        action.execute();

        verify(action).getText("error.encounter.addNewMeasurementGroup.noTypesSelected", new String[]{GROUP});
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "error", ""})
    void shouldWriteNothing_whenTheSubmitIsNotAnAdd(String forward) throws Exception {
        action.setForward(forward);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.INPUT);
        verify(creation, never()).create(anyString(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tab\tname", "<b>x</b>", "Vitals-BP"})
    void shouldRefuseANameTheDefineStepWouldNotAccept_withoutWriting(String name) {
        action.setGroupName(name);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(creation);
        assertThat(request.getSession().getAttribute("groupName")).isNull();
    }

    @Test
    void shouldDenyTheRequest_withoutEitherAdministratorPrivilege() {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin.measurements)");
        verifyNoInteractions(creation);
    }

    @Test
    void shouldAllowTheMeasurementAdministrator_withoutGeneralAdminWrite() throws Exception {
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(false);
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);
        when(creation.create(eq(GROUP), eq(4), any())).thenReturn(Outcome.CREATED);

        assertThat(action.execute()).isEqualTo(EctAddMeasurementGroup2Action.SUCCESS);
    }
}
