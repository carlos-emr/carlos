/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentDaySheetRow;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import java.sql.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("appointment")
class AppointmentDaySheetServiceUnitTest {
    private static final String DAY = "2026-11-06";
    private static final long UPDATED = 1793966400000L;
    private OscarAppointmentDao appointments;
    private AppointmentArchiveDao archives;
    private AppointmentDaySheetService service;

    @BeforeEach
    void setUp() {
        appointments = mock(OscarAppointmentDao.class);
        archives = mock(AppointmentArchiveDao.class);
        service = new AppointmentDaySheetService(appointments, archives);
    }

    @Test
    void shouldAcknowledgeOnlyVisibleRows_whenGroupPrivacyAndRosterFiltersOverlap() {
        Appointment visible = appointment(1, "P1");
        when(appointments.findForUpdate(1)).thenReturn(visible);
        List<AppointmentDaySheetRow> rows = List.of(row(1, "P1", "NR"), row(2, "P2", "NR"),
                row(3, "P1", "RO"), row(4, "P3", "NR"));

        assertThat(service.acknowledge(rows, Set.of("P1", "P2"), Set.of("P1", "P3"), true, "viewer"))
                .containsExactly(rows.get(0));

        assertThat(visible.getStatus()).isEqualTo("T");
        assertThat(visible.getLastUpdateUser()).isEqualTo("viewer");
        verify(appointments).findForUpdate(1);
        verify(appointments).merge(visible);
        verifyNoMoreInteractions(appointments);
        verify(archives).archiveAppointment(visible);
        verifyNoMoreInteractions(archives);
    }

    @Test
    void shouldKeepReportOrderAndArchiveOriginalStatus_whenAllProvidersAreVisible() {
        Appointment first = appointment(1, "P1");
        Appointment second = appointment(2, "P2");
        when(appointments.findForUpdate(1)).thenReturn(first);
        when(appointments.findForUpdate(2)).thenReturn(second);
        doAnswer(call -> {
            Appointment current = call.getArgument(0);
            assertThat(current.getStatus()).isEqualTo("t");
            assertThat(current.getLastUpdateUser()).isEqualTo("previous");
            return null;
        }).when(archives).archiveAppointment(any());
        List<AppointmentDaySheetRow> rows = List.of(row(2, "P2", null), row(1, "P1", "RO"));

        assertThat(service.acknowledge(rows, null, null, false, "viewer")).containsExactlyElementsOf(rows);

        var order = inOrder(appointments, archives);
        order.verify(appointments).findForUpdate(1);
        order.verify(archives).archiveAppointment(first);
        order.verify(appointments).merge(first);
        order.verify(appointments).findForUpdate(2);
        order.verify(archives).archiveAppointment(second);
        order.verify(appointments).merge(second);
    }

    @Test
    void shouldOnlyFilterWithoutWriting_whenPreparingReport() {
        List<AppointmentDaySheetRow> rows = List.of(row(1, "P1", "NR"), row(2, "P1", "RO"), row(3, "P2", "NR"));
        assertThat(service.prepare(rows, Set.of("P1"), null, true)).containsExactly(rows.get(0));
        verifyNoInteractions(appointments, archives);
    }

    @Test
    void shouldReturnNothingWithoutWriting_whenSelectedGroupOrPrivacyScopeHasNoProviders() {
        List<AppointmentDaySheetRow> rows = List.of(row(1, "P1", "NR"));
        assertThat(service.acknowledge(rows, Set.of(), null, false, "viewer")).isEmpty();
        assertThat(service.acknowledge(rows, null, Set.of(), false, "viewer")).isEmpty();
        verifyNoInteractions(appointments, archives);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "acknowledged", "cancelled", "provider", "date", "time"})
    void shouldOmitStaleRowsWithoutWriting_whenAppointmentChangedAfterSelection(String change) {
        Appointment current = appointment(1, "P1");
        switch (change) {
            case "acknowledged" -> current.setStatus("T");
            case "cancelled" -> current.setStatus("C");
            case "provider" -> current.setProviderNo("P2");
            case "date" -> current.setAppointmentDate(Date.valueOf("2026-11-07"));
            case "time" -> {
                current.setStartTime(java.sql.Time.valueOf("10:00:00"));
                current.setReason("Updated after report selection");
                current.setUpdateDateTime(new java.util.Date(UPDATED + 1000));
            }
            default -> { }
        }
        when(appointments.findForUpdate(1)).thenReturn("deleted".equals(change) ? null : current);

        assertThat(service.acknowledge(List.of(row(1, "P1", "NR")), null, null, false, "viewer")).isEmpty();

        verify(appointments).findForUpdate(1);
        verifyNoMoreInteractions(appointments);
        verifyNoInteractions(archives);
    }

    @Test
    void shouldAcknowledgeLegacyNullVersion_whenLockedAppointmentIsStillUnversioned() {
        Appointment current = appointment(1, "P1");
        current.setUpdateDateTime(null);
        when(appointments.findForUpdate(1)).thenReturn(current);
        AppointmentDaySheetRow row = row(1, "P1", "NR", "unversioned");

        assertThat(service.acknowledge(List.of(row), null, null, false, "viewer")).containsExactly(row);
        assertThat(current.getStatus()).isEqualTo("T");
        verify(archives).archiveAppointment(current);
    }

    @Test
    void shouldNotAcknowledgeAppointment_whenArchiveFails() {
        Appointment current = appointment(1, "P1");
        when(appointments.findForUpdate(1)).thenReturn(current);
        doThrow(new IllegalStateException("archive failed")).when(archives).archiveAppointment(current);

        assertThatThrownBy(() -> service.acknowledge(List.of(row(1, "P1", "NR")), null, null, false, "viewer"))
                .isInstanceOf(IllegalStateException.class).hasMessage("archive failed");

        assertThat(current.getStatus()).isEqualTo("t");
        assertThat(current.getLastUpdateUser()).isEqualTo("previous");
        verify(appointments, never()).merge(any());
    }

    private static Appointment appointment(int id, String provider) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setProviderNo(provider);
        appointment.setAppointmentDate(Date.valueOf(DAY));
        appointment.setStatus("t");
        appointment.setLastUpdateUser("previous");
        appointment.setUpdateDateTime(new java.util.Date(UPDATED));
        return appointment;
    }

    private static AppointmentDaySheetRow row(int id, String provider, String roster) {
        return row(id, provider, roster, Long.toString(UPDATED));
    }

    private static AppointmentDaySheetRow row(int id, String provider, String roster, String version) {
        return new AppointmentDaySheetRow(null, null, DAY, provider, null, null, null, null, null,
                null, null, null, null, null, null, null, roster, null, null, null, id, version);
    }
}
