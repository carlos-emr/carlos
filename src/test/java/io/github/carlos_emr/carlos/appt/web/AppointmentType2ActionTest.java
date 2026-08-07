/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.web;

import io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppointmentType2Action")
@Tag("unit")
@Tag("appointment")
class AppointmentType2ActionTest extends CarlosWebTestBase {

    private AppointmentTypeDao appointmentTypeDao;
    private TestAppointmentType2Action action;

    @BeforeEach
    void setUpAction() {
        appointmentTypeDao = mock(AppointmentTypeDao.class);
        action = new TestAppointmentType2Action(appointmentTypeDao);
    }

    @Test
    void shouldCreateAppointmentTypeWithValidatedValues() throws Exception {
        configureSave("30");
        action.setName("  Follow Up  ");
        action.setReason("Follow-up reason");
        action.setNotes("Bring results");
        action.setLocation("Main");
        action.setResources("Room 1");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<AppointmentType> captor = ArgumentCaptor.forClass(AppointmentType.class);
        verify(appointmentTypeDao).persist(captor.capture());
        AppointmentType saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Follow Up");
        assertThat(saved.getDuration()).isEqualTo(30);
        assertThat(saved.getReason()).isEqualTo("Follow-up reason");
        assertThat(action.getActionMessages()).isNotEmpty();
        assertThat(action.getId()).isNull();
        assertThat(action.getName()).isNull();
        assertThat(action.getDuration()).isNull();
    }

    @Test
    void shouldUpdateThenReturnToAddMode() throws Exception {
        AppointmentType existing = mock(AppointmentType.class);
        when(appointmentTypeDao.find(42)).thenReturn(existing);
        configureSave("45");
        addRequestParameter("id", "42");
        action.setId(42);
        action.setName("Updated Type");

        String result = executeAction(action);
        assertThat(result)
                .withFailMessage("result=%s errors=%s", result, action.getActionErrors())
                .isEqualTo(ActionSupport.SUCCESS);

        verify(existing).setName("Updated Type");
        verify(existing).setDuration(45);
        verify(appointmentTypeDao).find(42);
        verify(appointmentTypeDao).merge(existing);
        verify(appointmentTypeDao, never()).persist(existing);
        assertThat(action.getId()).isNull();
        assertThat(action.getName()).isNull();
        assertThat(action.getDuration()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "30:00", " 30 ", "abc", "-1", "2147483648"})
    void shouldRejectInvalidDurationsWithoutMutation(String duration) throws Exception {
        configureSave(duration);
        action.setName("Invalid Duration");

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(appointmentTypeDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectValuesThatExceedDatabaseColumns() throws Exception {
        configureSave("15");
        action.setName("Length Test");
        action.setReason("r".repeat(81));
        action.setNotes("n".repeat(81));
        action.setLocation("l".repeat(31));
        action.setResources("x".repeat(11));

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).hasSize(4);
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldRejectInvalidNamesWithoutMutation(String name) throws Exception {
        configureSave("15");
        action.setName(name);

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(appointmentTypeDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPreserveSubmittedValuesWhenValidationFails() throws Exception {
        configureSave("30:00");
        action.setName("Preserved Type");
        action.setReason("Preserved reason");

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getName()).isEqualTo("Preserved Type");
        assertThat(action.getDuration()).isEqualTo("30:00");
        assertThat(action.getReason()).isEqualTo("Preserved reason");
    }

    @Test
    void shouldLoadExistingTypeForEdit() throws Exception {
        AppointmentType existing = mock(AppointmentType.class);
        when(existing.getId()).thenReturn(42);
        when(existing.getName()).thenReturn("Consult");
        when(existing.getDuration()).thenReturn(60);
        when(existing.getReason()).thenReturn("Consult reason");
        when(appointmentTypeDao.find(42)).thenReturn(existing);
        addRequestParameter("oper", "edit");
        addRequestParameter("no", "42");

        String result = executeAction(action);
        assertThat(result)
                .withFailMessage("result=%s errors=%s", result, action.getActionErrors())
                .isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentTypeDao).find(42);
        assertThat(action.getId()).isEqualTo(42);
        assertThat(action.getName()).isEqualTo("Consult");
        assertThat(action.getDuration()).isEqualTo("60");
        assertThat(action.getReason()).isEqualTo("Consult reason");
    }

    @Test
    void shouldRejectInvalidIdentifierInsteadOfCreatingNewType() throws Exception {
        configureSave("30");
        addRequestParameter("id", "invalid");
        action.setName("Must Not Save");

        assertThat(executeAction(action)).isEqualTo("failure");
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectUpdateWhenTypeNoLongerExists() throws Exception {
        configureSave("30");
        addRequestParameter("id", "42");
        action.setId(42);
        action.setName("Missing Type");

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).isNotEmpty();
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(appointmentTypeDao, never()).merge(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldDeleteExistingTypeOnPost() throws Exception {
        mockRequest.setMethod("POST");
        addRequestParameter("oper", "del");
        addRequestParameter("no", "42");

        assertThat(executeAction(action)).isEqualTo("redirect");
        verify(appointmentTypeDao).remove(42);
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "del"})
    void shouldRejectMutationsOverGet(String operation) throws Exception {
        mockRequest.setMethod("GET");
        addRequestParameter("oper", operation);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(appointmentTypeDao, never()).remove(org.mockito.ArgumentMatchers.anyInt());
    }

    private void configureSave(String duration) {
        mockRequest.setMethod("POST");
        addRequestParameter("oper", "save");
        action.setDuration(duration);
    }

    private static final class TestAppointmentType2Action extends AppointmentType2Action {
        private final AppointmentTypeDao appointmentTypeDao;

        private TestAppointmentType2Action(AppointmentTypeDao appointmentTypeDao) {
            this.appointmentTypeDao = appointmentTypeDao;
        }

        @Override
        protected AppointmentTypeDao getAppointmentTypeDao() {
            return appointmentTypeDao;
        }

        @Override
        protected SiteDao getSiteDao() {
            throw new AssertionError("Site DAO should not be accessed when multisite mode is disabled");
        }

        @Override
        protected boolean isMultisitesEnabled() {
            return false;
        }

        @Override
        public String getText(String key) {
            return key;
        }
    }
}
