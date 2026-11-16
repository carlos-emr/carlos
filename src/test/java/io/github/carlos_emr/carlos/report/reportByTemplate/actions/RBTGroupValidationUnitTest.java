/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import io.github.carlos_emr.carlos.commn.dao.RBTGroupDao;
import io.github.carlos_emr.carlos.commn.model.RBTGroup;
import io.github.carlos_emr.carlos.managers.RBTGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the action boundary with the real group-name validation manager. */
@Tag("unit")
@Tag("report")
class RBTGroupValidationUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final RBTGroupDao groups = mock(RBTGroupDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        RBTGroupManager manager = new RBTGroupManager();
        injectDependency(manager, "rbtGroupDao", groups);
        injectDependency(manager, "securityInfoManager", security);
        registerMock(RBTGroupManager.class, manager);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        request.setMethod("POST");
        request.addParameter("tid", "11", "12");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    private String execute(boolean create) {
        return create ? new RBTAddGroup2Action().execute() : new RBTAddToGroup2Action().execute();
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(true, false).flatMap(create -> Stream.of(
                Arguments.of(create, null, "Report by Template group name is required"),
                Arguments.of(create, " \u00a0 ", "Report by Template group name is required"),
                Arguments.of(create, "a".repeat(256), "Report by Template group name exceeds 255 characters"),
                Arguments.of(create, "bad\nname", "Report by Template group name contains control characters")));
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void shouldReturnValidationMessage_forInvalidGroupNames(boolean create, String name, String message) {
        if (name != null) request.setParameter("groupName", name);
        assertThat(execute(create)).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("errormsg")).isEqualTo(message);
        verify(groups, never()).persist(any(RBTGroup.class));
    }

    @Test
    void shouldReturnDuplicateMessage_forAnExistingGroup() {
        request.setParameter("groupName", "Strase");
        when(groups.getByGroupName("Strase")).thenReturn(List.of(new RBTGroup()));
        assertThat(execute(true)).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute("errormsg"))
                .isEqualTo("A Report by Template group with this name already exists");
        verify(groups, never()).persist(any(RBTGroup.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistValidNames_andKeepTheExistingSuccessRoute(boolean create) {
        request.setParameter("groupName", "Clinic O'Brien");
        assertThat(execute(create)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute(create ? "group_view" : "groupName")).isEqualTo("Clinic O'Brien");
        assertThat(request.getAttribute("errormsg")).isNull();
        verify(groups, times(create ? 1 : 2)).persist(any(RBTGroup.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPreserveReportDenial_beforeValidatingNames(boolean create) {
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> execute(create)).isInstanceOf(SecurityException.class);
        assertThat(request.getAttribute("errormsg")).isNull();
        verifyNoInteractions(groups);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPreserveAdminDenial_beforeValidatingNames(boolean create) {
        when(security.hasPrivilege(any(), eq("_admin"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(false);
        assertThatThrownBy(() -> execute(create)).isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorised Access. Object[_admin]");
        assertThat(request.getAttribute("errormsg")).isNull();
        verifyNoInteractions(groups);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPropagateUnexpectedPersistenceFailures_forValidNames(boolean create) {
        request.setParameter("groupName", "Clinic");
        doThrow(new IllegalStateException("synthetic database refusal"))
                .when(groups).persist(any(RBTGroup.class));
        assertThatThrownBy(() -> execute(create)).isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic database refusal");
        assertThat(request.getAttribute("errormsg")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepTheExistingNoSelectionBehavior_whenNoTemplateIdsAreSubmitted(boolean omitted) {
        request.setParameter("groupName", "Clinic");
        if (omitted) request.removeParameter("tid");
        else request.setParameter("tid", new String[0]);
        assertThat(execute(false)).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(groups);
    }

    @Test
    void shouldKeepMalformedIdentifiers_outsideGroupNameValidation() {
        request.setParameter("groupName", "Clinic");
        request.setParameter("tid", "invalid");
        assertThatThrownBy(() -> execute(false)).isInstanceOf(NumberFormatException.class);
        assertThat(request.getAttribute("errormsg")).isNull();
        verifyNoInteractions(groups);
    }
}
