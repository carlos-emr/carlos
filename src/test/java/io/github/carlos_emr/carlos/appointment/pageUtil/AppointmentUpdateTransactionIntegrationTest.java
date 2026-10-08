/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.OtherId;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentUpdateTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private OscarAppointmentDao appointments;
    @Autowired private AppointmentArchiveDao archives;
    @Autowired private OtherIdDAO metadata;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entities;

    @ParameterizedTest
    @ValueSource(strings = {"archive", "appointment", "metadata", "rollbackOnly", "afterCommit"})
    void flushedWritesRemainAtomicAndAStaleRetryCannotOverwriteACommittedChange(String failure) throws Exception {
        int id = seed();
        try {
            String original = version(id);
            AtomicBoolean fail = new AtomicBoolean(true);
            var request = request(id, original);
            var response = new MockHttpServletResponse();
            var login = mock(LoggedInInfo.class);
            var security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(login, "_appointment", "w", (String) null)).thenReturn(true);
            var events = mock(EventService.class);
            var appointmentWrites = mock(OscarAppointmentDao.class);
            when(appointmentWrites.findForUpdate(id)).thenAnswer(call -> appointments.findForUpdate(id));
            doAnswer(call -> {
                appointments.merge(call.getArgument(0));
                entities.flush();
                inject(fail, failure, "appointment");
                return null;
            }).when(appointmentWrites).merge(any());
            var archiveWrites = mock(AppointmentArchiveDao.class);
            when(archiveWrites.archiveAppointment(any(Appointment.class))).thenAnswer(call -> {
                var archive = archives.archiveAppointment((Appointment) call.getArgument(0));
                entities.flush();
                inject(fail, failure, "archive");
                return archive;
            });
            PlatformTransactionManager completion = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return transactions.getTransaction(definition);
                }
                @Override public void commit(TransactionStatus status) {
                    if (fail.get() && "rollbackOnly".equals(failure)) status.setRollbackOnly();
                    transactions.commit(status);
                }
                @Override public void rollback(TransactionStatus status) { transactions.rollback(status); }
            };
            // Initialize the legacy facade before mocking SpringUtils so its cached DAO
            // cannot be initialized to null and leak into later integration tests.
            try (var ids = mockStatic(OtherIdManager.class);
                 var spring = mockStatic(SpringUtils.class);
                 var servlet = mockStatic(ServletActionContext.class);
                 var loggedIn = mockStatic(LoggedInInfo.class)) {
                spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                spring.when(() -> SpringUtils.getBean(OscarAppointmentDao.class)).thenReturn(appointmentWrites);
                spring.when(() -> SpringUtils.getBean(AppointmentArchiveDao.class)).thenReturn(archiveWrites);
                spring.when(() -> SpringUtils.getBean(EventService.class)).thenReturn(events);
                spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(completion);
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
                // Adapt the legacy static facade to the real production metadata DAO, keeping
                // failure injection after the actual flushed write within the action transaction.
                ids.when(() -> OtherIdManager.getApptOtherId(String.valueOf(id), "appt_mc_number"))
                        .thenAnswer(call -> {
                            OtherId row = metadata.getOtherId(2, id, "appt_mc_number");
                            return row == null ? "" : row.getOtherId();
                        });
                ids.when(() -> OtherIdManager.saveIdAppointment(String.valueOf(id), "appt_mc_number", "new-mc"))
                        .thenAnswer(call -> {
                            metadata.save(new OtherId(2, id, "appt_mc_number", "new-mc"));
                            entities.flush();
                            inject(fail, failure, "metadata");
                            if (fail.get() && "afterCommit".equals(failure)) {
                                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                    @Override public void afterCommit() { throw new IllegalStateException("Injected acknowledgement failure"); }
                                });
                            }
                            return null;
                        });
                assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("input");
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(request.getParameter("reason")).isEqualTo("New owned reason");
                boolean committed = "afterCommit".equals(failure);
                if (committed) verify(events).appointmentStatusChanged(any(), eq(String.valueOf(id)), eq("999998"), eq("C"));
                else verifyNoInteractions(events);
                assertRows(id, committed);
                fail.set(false);
                response.reset();
                if (!committed) {
                    assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("success");
                    assertRows(id, true);
                    verify(events).appointmentStatusChanged(any(), eq(String.valueOf(id)), eq("999998"), eq("C"));
                }
                response.reset();
                assertThat(new AppointmentUpdateRecord2Action().execute()).isEqualTo("input");
                assertThat(response.getStatus()).isEqualTo(409);
                assertRows(id, true);
            }
        } finally {
            cleanup(id);
        }
    }

    @Test
    void aLockedLookupRefreshesAnAppointmentAlreadyLoadedIntoThePersistenceContext() {
        int id = seed();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Appointment previouslyLoaded = appointments.find(id);
                assertThat(previouslyLoaded.getStatus()).isEqualTo("t");
                TransactionTemplate otherSession = new TransactionTemplate(transactions);
                otherSession.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                otherSession.executeWithoutResult(other -> {
                    Appointment current = appointments.find(id);
                    current.setStatus("C");
                    appointments.merge(current);
                });
                assertThat(appointments.findForUpdate(id).getStatus()).isEqualTo("C");
            });
        } finally {
            cleanup(id);
        }
    }

    private int seed() {
        return new TransactionTemplate(transactions).execute(status -> {
            Appointment row = new Appointment();
            row.setProviderNo("999998"); row.setName("Owned concurrency fixture");
            row.setAppointmentDate(java.sql.Date.valueOf("2026-08-01"));
            row.setStartTime(java.sql.Time.valueOf("09:00:00")); row.setEndTime(java.sql.Time.valueOf("09:15:00"));
            row.setReason("owned-" + UUID.randomUUID()); row.setNotes(""); row.setStatus("t");
            row.setCreator("999998"); row.setLastUpdateUser("999998"); row.setLocation("");
            row.setResources(""); row.setType(""); row.setStyle(""); row.setBilling(""); row.setRemarks(""); row.setUrgency("");
            appointments.persist(row); entities.flush();
            return row.getId();
        });
    }

    private String version(int id) {
        return new TransactionTemplate(transactions).execute(status -> AppointmentEditVersion.of(appointments.find(id), ""));
    }

    private MockHttpServletRequest request(int id, String original) {
        var request = new MockHttpServletRequest("POST", "/appointment/UpdateRecord");
        request.getSession().setAttribute("user", "999998");
        request.setParameter("appointment_no", String.valueOf(id));
        request.setParameter(AppointmentEditVersion.PARAMETER, original);
        request.setParameter("appointment_date", "2026-08-01"); request.setParameter("start_time", "09:00");
        request.setParameter("end_time", "09:15"); request.setParameter("demographic_no", "0");
        request.setParameter("keyword", "Owned concurrency fixture"); request.setParameter("reason", "New owned reason");
        request.setParameter("status", "C"); request.setParameter("appt_mc_number", "new-mc");
        for (String field : new String[]{"notes", "location", "resources", "type", "style", "billing", "remarks", "urgency"}) {
            request.setParameter(field, "");
        }
        return request;
    }

    private void assertRows(int id, boolean changed) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Appointment row = appointments.find(id);
            assertThat(row.getStatus()).isEqualTo(changed ? "C" : "t");
            if (changed) assertThat(row.getReason()).isEqualTo("New owned reason");
            else assertThat(row.getReason()).startsWith("owned-");
            assertThat(entities.createQuery("select count(a) from AppointmentArchive a where a.appointmentNo=:id", Long.class)
                    .setParameter("id", id).getSingleResult()).isEqualTo(changed ? 1L : 0L);
            assertThat(entities.createQuery("select count(o) from OtherId o where o.tableName=2 and o.tableId=:id and o.otherKey='appt_mc_number'", Long.class)
                    .setParameter("id", String.valueOf(id)).getSingleResult()).isEqualTo(changed ? 1L : 0L);
        });
    }

    private void cleanup(int id) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entities.createQuery("delete from OtherId o where o.tableName=2 and o.tableId=:id and o.otherKey='appt_mc_number'")
                    .setParameter("id", String.valueOf(id)).executeUpdate();
            entities.createQuery("delete from AppointmentArchive a where a.appointmentNo=:id").setParameter("id", id).executeUpdate();
            entities.createQuery("delete from Appointment a where a.id=:id").setParameter("id", id).executeUpdate();
        });
    }

    private static void inject(AtomicBoolean fail, String selected, String phase) {
        if (fail.get() && selected.equals(phase)) throw new IllegalStateException("Injected " + phase + " failure");
    }
}
