/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import jakarta.xml.ws.BindingProvider;

import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ScheduleManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentArchiveTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;

/**
 * The three timestamp-limited {@link ScheduleWs} operations as an unmodified client calls them: no
 * {@code X-Carlos-Schedule-*} header, and the next request's exclusive bound is the last update
 * timestamp the client saw.
 *
 * <p>The real service and the real {@link ScheduleManagerImpl} answer real SOAP requests; only the
 * two DAOs are stand-ins that seek through an in-memory table the way the production queries do.
 * Such a client can never be answered with a fault for a page boundary that falls inside a group of
 * rows sharing one update second, and must not lose the rest of that group either.</p>
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("ScheduleWs header-less timestamp synchronization")
class ScheduleLegacySyncEndpointTest extends CarlosSoapTestBase {

    private static final long FIRST_SECOND = 1_700_000_000_000L;

    @Mock private OscarAppointmentDao appointments;
    @Mock private AppointmentArchiveDao archives;
    @Mock private SecurityInfoManager security;
    @Mock private PatientConsentManager consent;

    private final List<Appointment> current = new ArrayList<>();
    private final List<AppointmentArchive> archived = new ArrayList<>();

    @Override
    protected Object getServiceBean() {
        ScheduleManagerImpl manager = new ScheduleManagerImpl();
        injectDependency(manager, "oscarAppointmentDao", appointments);
        injectDependency(manager, "appointmentArchiveDao", archives);
        injectDependency(manager, "securityInfoManager", security);
        injectDependency(manager, "patientConsentManager", consent);
        ScheduleWs service = new ScheduleWs();
        injectDependency(service, "scheduleManager", manager);
        return service;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return ScheduleWs.class;
    }

    @BeforeEach
    void storage() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("sync-reader");
        when(security.hasPrivilege(any(), eq("_appointment.UpdatedAfterDate"), eq("x"), isNull())).thenReturn(true);
        when(consent.filterProviderSpecificConsent(any(), anyList())).thenAnswer(call -> call.getArgument(1));
        when(appointments.findByUpdateDateAndId(any(), anyInt(), anyInt())).thenAnswer(call ->
                seek(current, Appointment::getUpdateDateTime, Appointment::getId,
                        call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(appointments.findByProgramProviderDemographicDateAndId(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(call -> seek(current, Appointment::getUpdateDateTime, Appointment::getId,
                        call.getArgument(3), call.getArgument(4), call.getArgument(5)));
        when(archives.findByUpdateDateAndId(any(), anyInt(), anyInt())).thenAnswer(call ->
                seek(archived, AppointmentArchive::getUpdateDateTime, AppointmentArchive::getId,
                        call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    }

    /** The production seek: strictly after (timestamp, id), ordered by timestamp then id, bounded. */
    private static <T> List<T> seek(List<T> table, Function<T, Date> updated, Function<T, Integer> id,
            Date after, int afterId, int limit) {
        return table.stream()
                .filter(row -> updated.apply(row).getTime() > after.getTime()
                        || updated.apply(row).getTime() == after.getTime() && id.apply(row) > afterId)
                .sorted(Comparator.comparing((T row) -> updated.apply(row).getTime()).thenComparing(id))
                .limit(limit).toList();
    }

    /** Stores one current and one archived row with this id, updated in the given whole second. */
    private void store(int id, int second) {
        Date updated = new Date(FIRST_SECOND + second * 1000L);
        Appointment row = new Appointment();
        row.setId(id);
        row.setProviderNo("p");
        row.setDemographicNo(42);
        row.setProgramId(5);
        row.setAppointmentDate(java.sql.Date.valueOf("2026-07-15"));
        row.setStartTime(java.sql.Time.valueOf("09:30:00"));
        row.setEndTime(java.sql.Time.valueOf("09:45:00"));
        row.setUpdateDateTime(updated);
        current.add(row);
        AppointmentArchive old = new AppointmentArchive();
        ReflectionTestUtils.setField(old, "id", id);
        old.setAppointmentNo(id);
        old.setProviderNo("p");
        old.setAppointmentDate(java.sql.Date.valueOf("2026-07-15"));
        old.setStartTime(java.sql.Time.valueOf("09:30:00"));
        old.setEndTime(java.sql.Time.valueOf("09:45:00"));
        old.setUpdateDateTime(updated);
        archived.add(old);
    }

    /**
     * One row, then four sharing a second, one more, two sharing a second, and a last one. With a
     * page size of two, both shared seconds straddle a page boundary.
     */
    private void storeGroupsThatStraddlePageBoundaries() {
        store(1, 1);
        for (int id = 2; id <= 5; id++) store(id, 2);
        store(6, 3);
        store(7, 4);
        store(8, 4);
        store(9, 5);
    }

    /**
     * An unmodified client: timestamp-only requests, each bounded by the newest update timestamp
     * seen so far, until a page comes back empty.
     */
    private <T> List<Integer> synchronize(BiFunction<Date, Integer, T[]> request, Function<T, Integer> id,
            Function<T, Calendar> updated, int pageSize, List<Integer> pageSizes) {
        List<Integer> seen = new ArrayList<>();
        Date after = new Date(0);
        for (int requests = 0; requests < 50; requests++) {
            T[] page = request.apply(after, pageSize);
            if (page == null || page.length == 0) return seen;
            pageSizes.add(page.length);
            Date newest = after;
            for (T row : page) {
                seen.add(id.apply(row));
                if (updated.apply(row).getTime().after(newest)) newest = updated.apply(row).getTime();
            }
            assertThat(newest).as("a non-empty page must move the client's bound forward").isAfter(after);
            after = newest;
        }
        throw new AssertionError("The client never reached an empty page");
    }

    private static List<Integer> ids(int first, int last) {
        return IntStream.rangeClosed(first, last).boxed().toList();
    }

    @Test
    @DisplayName("should answer a header-less page whose boundary shares a timestamp without a fault")
    void shouldAnswerWithoutAFault_whenAHeaderlessPageBoundarySharesATimestamp() {
        storeGroupsThatStraddlePageBoundaries();
        ScheduleWs proxy = createClient(ScheduleWs.class);

        AppointmentTransfer[] page = proxy.getAppointmentsUpdatedAfterDate(new Date(0), 2, false);

        // Rows 1 and 2 fill the requested page; 3 to 5 share row 2's second and complete its group.
        assertThat(page).extracting(AppointmentTransfer::getId).containsExactly(1, 2, 3, 4, 5);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) ((BindingProvider) proxy)
                .getResponseContext().get(Message.PROTOCOL_HEADERS);
        assertThat(headers).containsEntry(ScheduleSyncHeaders.PROFILE, List.of("legacy-timestamp"));
    }

    @Test
    @DisplayName("should lose no current appointment across consecutive header-less pages")
    void shouldLoseNoCurrentAppointment_acrossConsecutiveHeaderlessPages() {
        storeGroupsThatStraddlePageBoundaries();
        ScheduleWs proxy = createClient(ScheduleWs.class);
        List<Integer> pageSizes = new ArrayList<>();

        List<Integer> seen = synchronize((after, size) -> proxy.getAppointmentsUpdatedAfterDate(after, size, false),
                AppointmentTransfer::getId, AppointmentTransfer::getUpdateDateTime, 2, pageSizes);

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
        assertThat(pageSizes).containsExactly(5, 3, 1);
    }

    @Test
    @DisplayName("should lose no archived appointment across consecutive header-less pages")
    void shouldLoseNoArchivedAppointment_acrossConsecutiveHeaderlessPages() {
        storeGroupsThatStraddlePageBoundaries();
        ScheduleWs proxy = createClient(ScheduleWs.class);

        List<Integer> seen = synchronize(
                (after, size) -> proxy.getAppointmentArchivesUpdatedAfterDate(after, size, false),
                AppointmentArchiveTransfer::getId, AppointmentArchiveTransfer::getUpdateDateTime, 2, new ArrayList<>());

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
    }

    @Test
    @DisplayName("should lose no filtered appointment across consecutive header-less pages")
    void shouldLoseNoFilteredAppointment_acrossConsecutiveHeaderlessPages() {
        storeGroupsThatStraddlePageBoundaries();
        ScheduleWs proxy = createClient(ScheduleWs.class);

        List<Integer> seen = synchronize((after, size) -> {
            Calendar bound = Calendar.getInstance();
            bound.setTime(after);
            return proxy.getAppointmentsByProgramProviderDemographicDate(5, "p", 42, bound, size, false);
        }, AppointmentTransfer::getId, AppointmentTransfer::getUpdateDateTime, 2, new ArrayList<>());

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
    }

    @Test
    @DisplayName("should keep the requested page size when no timestamp is shared across the boundary")
    void shouldKeepTheRequestedPageSize_whenNoTimestampIsSharedAcrossTheBoundary() {
        for (int id = 1; id <= 5; id++) store(id, id);
        ScheduleWs proxy = createClient(ScheduleWs.class);

        assertThat(proxy.getAppointmentsUpdatedAfterDate(new Date(0), 2, false))
                .extracting(AppointmentTransfer::getId).containsExactly(1, 2);
    }
}
