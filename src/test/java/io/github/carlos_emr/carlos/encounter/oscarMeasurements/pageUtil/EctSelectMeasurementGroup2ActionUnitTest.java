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

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused GET-rejection coverage for {@link EctSelectMeasurementGroup2Action}, a conditional
 * mutator in {@code MutatorActionGetRejectionContractUnitTest}: only {@code forward=delete}
 * writes, so only it must be a POST (issue #3335).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("EctSelectMeasurementGroup2Action")
class EctSelectMeasurementGroup2ActionUnitTest extends CarlosUnitTestBase {

    private static final String GROUP = "FAKE Synthetic vitals";

    private MeasurementGroupStyleDao styleDao;
    private MeasurementGroupDao groupDao;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private EctSelectMeasurementGroup2Action action;

    @BeforeEach
    void setUp() {
        styleDao = mock(MeasurementGroupStyleDao.class);
        groupDao = mock(MeasurementGroupDao.class);
        security = mock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        registerMock(MeasurementGroupStyleDao.class, styleDao);
        registerMock(MeasurementGroupDao.class, groupDao);
        registerMock(SecurityInfoManager.class, security);
        registerMock(MeasurementManager.class, mock(MeasurementManager.class));

        request = new MockHttpServletRequest("POST", "/encounter/oscarMeasurements/SelectMeasurementGroup");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(true);

        action = new EctSelectMeasurementGroup2Action();
        action.setSelectedGroupName(GROUP);
        action.setForward("delete");
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseTheGroupDeletion_whenNotPosted(String method) throws Exception {
        request.setMethod(method);

        assertThat(action.execute()).isEqualTo(EctSelectMeasurementGroup2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(styleDao, groupDao, security);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldStillOpenTheTypeEditor_whenRequestedByGet() throws Exception {
        request.setMethod("GET");
        action.setForward("type");

        assertThat(action.execute()).isEqualTo("type");
        verifyNoInteractions(styleDao, groupDao);
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo(GROUP);
    }

    @Test
    void shouldRemoveTheGroupAndItsMembers_whenDeletionIsPosted() throws Exception {
        MeasurementGroupStyle style = new MeasurementGroupStyle();
        style.setId(7);
        MeasurementGroup member = new MeasurementGroup();
        member.setId(11);
        when(styleDao.findByGroupName(GROUP)).thenReturn(List.of(style));
        when(groupDao.findByName(GROUP)).thenReturn(List.of(member));

        assertThat(action.execute()).isEqualTo("delete");
        verify(styleDao).remove(7);
        verify(groupDao).remove(11);
    }

    @Test
    void shouldDenyThePostedDeletion_withoutEitherAdministratorPrivilege() {
        when(security.hasPrivilege(login, "_admin.measurements", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(styleDao, groupDao);
    }
}
