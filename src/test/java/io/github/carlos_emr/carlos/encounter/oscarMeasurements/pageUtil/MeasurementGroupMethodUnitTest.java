/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.MeasurementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class MeasurementGroupMethodUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MeasurementGroupDao groups;
    private MeasurementGroupStyleDao styles;
    private MeasurementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.getSession().setAttribute("groupName", "original");
        groups = mock(MeasurementGroupDao.class);
        styles = mock(MeasurementGroupStyleDao.class);
        manager = mock(MeasurementManager.class);
        security = mock(SecurityInfoManager.class);
        registerMock(MeasurementGroupDao.class, groups);
        registerMock(MeasurementGroupStyleDao.class, styles);
        registerMock(MeasurementManager.class, manager);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() { servlet.close(); }

    private EctEditMeasurementGroup2Action edit(String forward) {
        EctEditMeasurementGroup2Action action = new EctEditMeasurementGroup2Action();
        action.setForward(forward);
        action.setGroupName("owned");
        action.setSelectedAddTypes(new String[]{"BP"});
        action.setSelectedDeleteTypes(new String[]{"BP"});
        return action;
    }

    private EctSelectMeasurementGroup2Action select(String forward) {
        EctSelectMeasurementGroup2Action action = new EctSelectMeasurementGroup2Action();
        action.setForward(forward);
        action.setSelectedGroupName("owned");
        return action;
    }

    @ParameterizedTest
    @CsvSource({"GET,add", "HEAD,add", "GET,delete", "HEAD,delete"})
    void shouldRefuseTypeMutation_whenReadMethodIsUsed(String method, String forward) throws Exception {
        request.setMethod(method);
        assertThat(edit(forward).execute()).isEqualTo(ActionSupport.NONE);
        assertRejectedWithoutMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRefuseWholeGroupDeletion_whenMethodIsNotPost(String method) throws Exception {
        request.setMethod(method);
        assertThat(select("delete").execute()).isEqualTo(ActionSupport.NONE);
        assertRejectedWithoutMutation();
    }

    private void assertRejectedWithoutMutation() {
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo("original");
        verifyNoInteractions(groups, styles, manager);
    }

    @Test
    void shouldPersistSelectedType_whenAuthorizedPostAddsIt() throws Exception {
        request.setMethod("POST");
        assertThat(edit("add").execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(groups).persist(argThat(group -> "owned".equals(group.getName()) && "BP".equals(group.getTypeDisplayName())));
        verifyNoMoreInteractions(groups);
    }

    @Test
    void shouldRemoveOnlySelectedType_whenAuthorizedPostDeletesIt() throws Exception {
        request.setMethod("POST");
        MeasurementGroup group = new MeasurementGroup();
        group.setId(7);
        when(groups.findByNameAndTypeDisplayName("owned", "BP")).thenReturn(List.of(group));
        assertThat(edit("delete").execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(groups).remove(7);
        verifyNoInteractions(styles);
    }

    @Test
    void shouldRemoveGroupAndStyles_whenAuthorizedPostDeletesWholeGroup() throws Exception {
        request.setMethod("POST");
        MeasurementGroup group = new MeasurementGroup();
        group.setId(7);
        MeasurementGroupStyle style = new MeasurementGroupStyle();
        style.setId(8);
        when(groups.findByName("owned")).thenReturn(List.of(group));
        when(styles.findByGroupName("owned")).thenReturn(List.of(style));
        assertThat(select("delete").execute()).isEqualTo("delete");
        verify(groups).remove(7);
        verify(styles).remove(8);
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo("owned");
    }

    @Test
    void shouldPreserveGetNavigation_withoutPersistingChanges() throws Exception {
        request.setMethod("GET");
        assertThat(edit(null).execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(select("type").execute()).isEqualTo("type");
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo("owned");
        verifyNoInteractions(groups, styles, manager);
    }

    @Test
    void shouldRefuseBothDeleteActions_whenAdminWriteIsDenied() {
        request.setMethod("POST");
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> edit("delete").execute()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> select("delete").execute()).isInstanceOf(SecurityException.class);
        assertThat(request.getSession().getAttribute("groupName")).isEqualTo("original");
        verifyNoInteractions(groups, styles, manager);
    }
}
