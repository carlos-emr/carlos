/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.managers.AppointmentUpdatePage;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.ScheduleSyncBoundaryException;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import jakarta.xml.ws.BindingProvider;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
class ScheduleSyncEndpointTest extends CarlosSoapTestBase {
    @Mock private ScheduleManager schedules;
    @Override protected Object getServiceBean() {
        var service = new ScheduleWs(); injectDependency(service,"scheduleManager",schedules); return service;
    }
    @Override protected Class<?> getServiceInterface() { return ScheduleWs.class; }
    @BeforeEach void actor() { when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("sync-reader"); }
    private static Appointment row(int id) {
        var row = new Appointment(); row.setId(id); row.setUpdateDateTime(new Date(1000));
        row.setAppointmentDate(java.sql.Date.valueOf("2026-07-15"));
        row.setStartTime(java.sql.Time.valueOf("09:30:00")); row.setEndTime(java.sql.Time.valueOf("09:45:00"));
        return row;
    }
    private ScheduleWs client(Map<String,List<String>> headers) {
        var proxy = createClient(ScheduleWs.class);
        ((BindingProvider)proxy).getRequestContext().put(Message.PROTOCOL_HEADERS,headers); return proxy;
    }
    @SuppressWarnings("unchecked")
    private Map<String,List<String>> headers(ScheduleWs proxy) {
        return (Map<String,List<String>>) ((BindingProvider)proxy).getResponseContext().get(Message.PROTOCOL_HEADERS);
    }
    @Test void shouldContinueWithinTheSameSecond_acrossRealSoapRequests_withoutChangingTheWsdlBody() {
        when(schedules.getAppointmentUpdatedPage(any(),eq(new Date(0)),eq(Integer.MAX_VALUE),eq(1)))
                .thenReturn(new AppointmentUpdatePage<>(List.of(row(1)),true,1000L,1,1000L));
        when(schedules.getAppointmentUpdatedPage(any(),eq(new Date(1000)),eq(1),eq(1)))
                .thenReturn(new AppointmentUpdatePage<>(List.of(row(2)),false,1000L,2,null));
        var first = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")));
        assertThat(first.getAppointmentsUpdatedAfterDate(new Date(0),1,false)).extracting(value -> value.getId()).containsExactly(1);
        assertThat(headers(first)).containsEntry(ScheduleSyncHeaders.MORE,List.of("true"))
                .containsEntry("X-Carlos-Schedule-Reconciliation",List.of("required"));
        String cursor = headers(first).get(ScheduleSyncHeaders.NEXT).getFirst();
        var second = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id"),ScheduleSyncHeaders.CURSOR,List.of(cursor)));
        assertThat(second.getAppointmentsUpdatedAfterDate(new Date(0),1,false)).extracting(value -> value.getId()).containsExactly(2);
        assertThat(headers(second)).containsEntry(ScheduleSyncHeaders.MORE,List.of("false"));
        verify(schedules,never()).getAppointmentUpdatedAfterDate(any(),any(),anyInt());
    }
    @Test void shouldSupportTheFilteredOperation_withContextBoundContinuation() {
        var bound = Calendar.getInstance(); bound.setTimeInMillis(0);
        when(schedules.getAppointmentsByProgramProviderDemographicPage(any(),eq(5),eq("p"),eq(42),eq(new Date(0)),eq(Integer.MAX_VALUE),eq(1)))
                .thenReturn(new AppointmentUpdatePage<>(List.of(row(1)),false,1000L,1,null));
        var proxy = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")));
        assertThat(proxy.getAppointmentsByProgramProviderDemographicDate(5,"p",42,bound,1,false)).hasSize(1);
        assertThat(headers(proxy)).containsEntry(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id"));
        assertThat(headers(proxy)).containsKey(ScheduleSyncHeaders.NEXT);
    }
    @Test void shouldSupportArchiveContinuation_withoutAddingASoapOperation() {
        var row = new AppointmentArchive(); org.springframework.test.util.ReflectionTestUtils.setField(row,"id",8);
        row.setUpdateDateTime(new Date(1000));
        when(schedules.getAppointmentArchiveUpdatedPage(any(),eq(new Date(0)),eq(Integer.MAX_VALUE),eq(1)))
                .thenReturn(new AppointmentUpdatePage<>(List.of(row),false,1000L,8,null));
        var proxy = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")));
        assertThat(proxy.getAppointmentArchivesUpdatedAfterDate(new Date(0),1,false)).hasSize(1);
        assertThat(headers(proxy)).containsEntry(ScheduleSyncHeaders.MORE,List.of("false"));
    }
    @Test void shouldRefuseCursorReuse_whenTheOperationActorOrOriginalBoundChanges() {
        when(schedules.getAppointmentUpdatedPage(any(),any(),anyInt(),anyInt()))
                .thenReturn(new AppointmentUpdatePage<>(List.of(row(1)),false,1000L,1,null));
        var first = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")));
        first.getAppointmentsUpdatedAfterDate(new Date(0),1,false);
        String cursor = headers(first).get(ScheduleSyncHeaders.NEXT).getFirst(); clearInvocations(schedules);
        var proxy = client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id"),ScheduleSyncHeaders.CURSOR,List.of(cursor)));
        assertThatThrownBy(() -> proxy.getAppointmentArchivesUpdatedAfterDate(new Date(0),1,false)).hasMessageContaining("does not match");
        assertThatThrownBy(() -> proxy.getAppointmentsUpdatedAfterDate(new Date(1),1,false)).hasMessageContaining("does not match");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("another-reader");
        assertThatThrownBy(() -> proxy.getAppointmentsUpdatedAfterDate(new Date(0),1,false)).hasMessageContaining("does not match");
        verifyNoInteractions(schedules);
    }
    @Test void shouldFaultLegacyTieBoundaries_insteadOfReturningAnApparentlyCompletePartialPage() {
        when(schedules.getAppointmentUpdatedAfterDate(any(),any(),anyInt())).thenThrow(new ScheduleSyncBoundaryException());
        assertThatThrownBy(() -> client(Map.of()).getAppointmentsUpdatedAfterDate(new Date(0),1,false))
                .hasMessageContaining("splits a shared update timestamp");
    }
    @Test void shouldRejectMalformedDuplicateAndUnselectedCursorHeaders_beforeCallingTheManager() {
        for (var headers : List.of(Map.of(ScheduleSyncHeaders.PROFILE,List.of("unknown")),
                Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id","timestamp-id")),
                Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id"),ScheduleSyncHeaders.CURSOR,List.of("bad")),
                Map.of(ScheduleSyncHeaders.CURSOR,List.of("bad")))) {
            assertThatThrownBy(() -> client(headers).getAppointmentsUpdatedAfterDate(new Date(0),1,false));
        }
        verifyNoInteractions(schedules);
    }
    @Test void shouldFaultStorageAndInconsistentPositions_withoutLeakingPrivateFailureMessages() {
        when(schedules.getAppointmentUpdatedPage(any(),any(),anyInt(),anyInt()))
                .thenThrow(new IllegalStateException("PRIVATE_patient_record.sql"));
        assertThatThrownBy(() -> client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")))
                .getAppointmentsUpdatedAfterDate(new Date(0),1,false)).hasMessageContaining("Schedule synchronization failed").hasMessageNotContaining("PRIVATE");
        doReturn(new AppointmentUpdatePage<>(List.of(row(1)),true,1000L,999,1000L))
                .when(schedules).getAppointmentUpdatedPage(any(),any(),anyInt(),anyInt());
        assertThatThrownBy(() -> client(Map.of(ScheduleSyncHeaders.PROFILE,List.of("timestamp-id")))
                .getAppointmentsUpdatedAfterDate(new Date(0),1,false)).hasMessageContaining("Schedule synchronization failed");
    }
}
