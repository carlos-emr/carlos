/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.web;

import io.github.carlos_emr.carlos.commn.dao.AppointmentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.Site;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppointmentType2Action")
@Tag("unit")
@Tag("appointment")
class AppointmentType2ActionUnitTest extends CarlosWebTestBase {

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
        action.setReason("  Follow-up reason  ");
        action.setNotes("  Bring results  ");
        action.setLocation("  Main  ");
        action.setResources("  Room 1  ");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<AppointmentType> captor = ArgumentCaptor.forClass(AppointmentType.class);
        verify(appointmentTypeDao).persist(captor.capture());
        AppointmentType saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Follow Up");
        assertThat(saved.getDuration()).isEqualTo(30);
        assertThat(saved.getReason()).isEqualTo("Follow-up reason");
        assertThat(saved.getNotes()).isEqualTo("Bring results");
        assertThat(saved.getLocation()).isEqualTo("Main");
        assertThat(saved.getResources()).isEqualTo("Room 1");
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
        action.setLocation("l".repeat(256));
        action.setResources("x".repeat(11));

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).containsExactlyInAnyOrder(
                "appointment.type.reason.length.error",
                "appointment.type.notes.length.error",
                "appointment.type.location.length.error",
                "appointment.type.resources.length.error");
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void shouldAcceptValuesAtAndImmediatelyBelowColumnLimits(int offset) throws Exception {
        configureSave("15");
        action.setName("n".repeat(50 + offset));
        action.setReason("r".repeat(80 + offset));
        action.setNotes("o".repeat(80 + offset));
        action.setLocation("l".repeat(255 + offset));
        action.setResources("x".repeat(10 + offset));

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentTypeDao).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAcceptAFullLengthMultisiteName() throws Exception {
        SiteDao siteDao = mock(SiteDao.class);
        Site site = mock(Site.class);
        String siteName = "s".repeat(255);
        when(site.getName()).thenReturn(siteName);
        when(site.getSiteId()).thenReturn(7);
        when(siteDao.getAllActiveSites()).thenReturn(List.of(site));
        action.enableMultisites(siteDao);
        configureSave("15");
        action.setName("Multisite Type");
        action.setLocation(siteName);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<AppointmentType> captor = ArgumentCaptor.forClass(AppointmentType.class);
        verify(appointmentTypeDao).persist(captor.capture());
        assertThat(captor.getValue().getLocation()).isEqualTo(siteName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "Unknown Site"})
    void shouldRejectUnknownLocationWhenMultisitesEnabled(String location) throws Exception {
        SiteDao siteDao = mock(SiteDao.class);
        Site site = mock(Site.class);
        when(site.getName()).thenReturn("Main Site");
        when(siteDao.getAllActiveSites()).thenReturn(List.of(site));
        action.enableMultisites(siteDao);
        configureSave("15");
        action.setName("Multisite Type");
        action.setLocation(location);

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).contains("appointment.type.location.error");
        verify(appointmentTypeDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAllowFreeTextLocationWhenMultisiteHasNoActiveSites() throws Exception {
        SiteDao siteDao = mock(SiteDao.class);
        when(siteDao.getAllActiveSites()).thenReturn(List.of());
        action.enableMultisites(siteDao);
        configureSave("15");
        action.setName("Remote Type");
        action.setLocation("Remote");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        verify(appointmentTypeDao).persist(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldRejectInvalidNamesWithoutMutation(String name) throws Exception {
        configureSave("15");
        action.setName(name);

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).containsExactly("appointment.type.name.error");
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
    void shouldIgnoreInvalidIdentifierOnPlainListView() throws Exception {
        mockRequest.setMethod("GET");
        addRequestParameter("id", "invalid");
        addRequestParameter("no", "also-invalid");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getActionErrors()).isEmpty();
        verify(appointmentTypeDao, never()).find(org.mockito.ArgumentMatchers.anyInt());
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

        mockRequest.removeAllParameters();
        mockRequest.setMethod("GET");
        TestAppointmentType2Action redirectedAction = new TestAppointmentType2Action(appointmentTypeDao);
        assertThat(executeAction(redirectedAction)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(redirectedAction.getActionMessages())
                .containsExactly("appointment.type.deleted.message");

        TestAppointmentType2Action refreshedAction = new TestAppointmentType2Action(appointmentTypeDao);
        assertThat(executeAction(refreshedAction)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(refreshedAction.getActionMessages()).isEmpty();
    }

    @Test
    void shouldReportDeleteFailure() throws Exception {
        doThrow(new RuntimeException("database failure")).when(appointmentTypeDao).remove(42);
        mockRequest.setMethod("POST");
        addRequestParameter("oper", "del");
        addRequestParameter("no", "42");

        assertThat(executeAction(action)).isEqualTo("failure");
        assertThat(action.getActionErrors()).containsExactly("appointment.type.delete.error");
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
        private SiteDao siteDao;
        private boolean multisitesEnabled;

        private TestAppointmentType2Action(AppointmentTypeDao appointmentTypeDao) {
            this.appointmentTypeDao = appointmentTypeDao;
        }

        @Override
        protected AppointmentTypeDao getAppointmentTypeDao() {
            return appointmentTypeDao;
        }

        @Override
        protected SiteDao getSiteDao() {
            if (siteDao == null) {
                throw new AssertionError("Site DAO should not be accessed when multisite mode is disabled");
            }
            return siteDao;
        }

        @Override
        protected boolean isMultisitesEnabled() {
            return multisitesEnabled;
        }

        private void enableMultisites(SiteDao siteDao) {
            this.siteDao = siteDao;
            this.multisitesEnabled = true;
        }

        @Override
        public String getText(String key) {
            return key;
        }
    }
}
