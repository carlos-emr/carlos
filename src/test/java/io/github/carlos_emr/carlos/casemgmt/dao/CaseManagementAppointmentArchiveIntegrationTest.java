/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/** The service transaction must commit or roll back the note, old archive and new status together. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CaseManagementAppointmentArchiveIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("CaseManagementNoteExtDAO") private CaseManagementNoteExtDAO extensions;
    @Autowired @Qualifier("CaseManagementNoteLinkDAO") private CaseManagementNoteLinkDAO links;
    @Autowired private HashAuditDao hashes;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCommitOriginalArchiveAndNewStatus_withTheSuccessfulNote(boolean verify) throws Exception {
        exercise(verify, "success");
    }

    @ParameterizedTest
    @ValueSource(strings = {"archive", "merge"})
    void shouldRollBackAllClinicalWrites_whenAnAppointmentWriteFailsLate(String failure) throws Exception {
        exercise(false, failure);
    }

    @Test
    void shouldRefreshTheCachedAppointment_whenAnotherSessionChangesItBeforeTheLock() throws Exception {
        exercise(false, "refresh");
    }

    private void exercise(boolean verify, String failure) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String uuid = UUID.randomUUID().toString();
        boolean successful = failure.equals("success") || failure.equals("refresh");
        int appointmentId = tx.execute(status -> {
            Appointment appointment = new Appointment();
            appointment.setDemographicNo(1);
            appointment.setProviderNo("999998");
            appointment.setAppointmentDate(new Date());
            appointment.setStartTime(new Date());
            appointment.setEndTime(new Date());
            appointment.setStatus("t");
            appointment.setLastUpdateUser("previous");
            entityManager.persist(appointment);
            entityManager.flush();
            return appointment.getId();
        });
        try {
            OscarAppointmentDaoImpl realAppointments = new OscarAppointmentDaoImpl();
            ReflectionTestUtils.setField(realAppointments, "entityManager", entityManager);
            AppointmentArchiveDaoImpl realArchives = new AppointmentArchiveDaoImpl();
            ReflectionTestUtils.setField(realArchives, "entityManager", entityManager);
            OscarAppointmentDao appointments = mock(OscarAppointmentDao.class, delegatesTo(realAppointments));
            AppointmentArchiveDao archives = mock(AppointmentArchiveDao.class, delegatesTo(realArchives));
            doAnswer(call -> {
                int id = call.getArgument(0);
                if (failure.equals("refresh")) {
                    // Simulate the action's earlier patient/appointment binding read.
                    assertThat(realAppointments.find(id).getStatus()).isEqualTo("t");
                    try (var connection = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class)
                            .getSessionFactoryOptions().getServiceRegistry().getService(org.hibernate.engine.jdbc.connections.spi.ConnectionProvider.class).getConnection();
                         var statement = connection.createStatement()) {
                        connection.setAutoCommit(true);
                        statement.executeUpdate("UPDATE appointment SET status='H', lastupdateuser='concurrent' WHERE appointment_no=" + id);
                    }
                }
                Appointment locked = realAppointments.findForUpdate(id);
                // A competing connection must not acquire this row while the note save owns it.
                try (var connection = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class)
                        .getSessionFactoryOptions().getServiceRegistry().getService(org.hibernate.engine.jdbc.connections.spi.ConnectionProvider.class).getConnection();
                     var statement = connection.createStatement()) {
                    boolean autoCommit = connection.getAutoCommit();
                    int timeout;
                    try (var setting = statement.executeQuery("CALL LOCK_TIMEOUT()")) {
                        setting.next();
                        timeout = setting.getInt(1);
                    }
                    try {
                        connection.setAutoCommit(false);
                        statement.execute("SET LOCK_TIMEOUT 50");
                        assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM appointment WHERE appointment_no=" + id + " FOR UPDATE"))
                                .isInstanceOf(java.sql.SQLException.class);
                    } finally {
                        connection.rollback();
                        statement.execute("SET LOCK_TIMEOUT " + timeout);
                        connection.setAutoCommit(autoCommit);
                    }
                }
                return locked;
            }).when(appointments).findForUpdate(any());
            if (failure.equals("archive")) doAnswer(call -> {
                realArchives.archiveAppointment(call.getArgument(0));
                entityManager.flush();
                throw new IllegalStateException("archive write failed");
            }).when(archives).archiveAppointment(any());
            if (failure.equals("merge")) doAnswer(call -> {
                realAppointments.merge(call.getArgument(0));
                entityManager.flush();
                throw new IllegalStateException("appointment merge failed");
            }).when(appointments).merge(any());
            CaseManagementManagerImpl target = spy(new CaseManagementManagerImpl());
            target.setCaseManagementNoteDAO(caseManagementNoteDAO);
            target.setCaseManagementNoteExtDAO(extensions);
            target.setCaseManagementNoteLinkDAO(links);
            ReflectionTestUtils.setField(target, "hashAuditDao", hashes);
            ReflectionTestUtils.setField(target, "eChartDao", mock(EChartDao.class));
            ReflectionTestUtils.setField(target, "appointmentArchiveDao", archives);
            doNothing().when(target).saveAndUpdateCaseIssues(anyList());
            doNothing().when(target).deleteTmpSave(any(), any(), any());
            doReturn("doctor").when(target).getRoleName(any(), any());
            doReturn("signature").when(target).getSignature(any(), any(), any(), any(), anyInt());
            ProxyFactory factory = new ProxyFactory(target);
            factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
            CaseManagementManager service = (CaseManagementManager) factory.getProxy();
            CaseManagementNote note = new CaseManagementNote();
            note.setUuid(uuid);
            note.setDemographic_no("1");
            note.setProviderNo("999998");
            note.setProgram_no("0");
            note.setNote("Atomic appointment assessment");
            note.setSigned(true);
            note.setAppointmentNo(appointmentId);
            try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class)) {
                beans.when(() -> SpringUtils.getBean(ProgramManager.class)).thenReturn(mock(ProgramManager.class));
                beans.when(() -> SpringUtils.getBean(AdmissionManager.class)).thenReturn(mock(AdmissionManager.class));
                beans.when(() -> SpringUtils.getBean(OscarAppointmentDao.class)).thenReturn(appointments);
                var save = (org.assertj.core.api.ThrowableAssert.ThrowingCallable) () -> service.saveCaseManagementNote(
                        mock(LoggedInInfo.class), note, List.of(), null, null, verify, Locale.ENGLISH,
                        new Date(), "Doctor", "999998", "192.0.2.1", "");
                if (successful) assertThatCode(save).doesNotThrowAnyException();
                else assertThatThrownBy(save).isInstanceOf(IllegalStateException.class);
            }
            tx.executeWithoutResult(status -> {
                Appointment saved = entityManager.find(Appointment.class, appointmentId);
                List<AppointmentArchive> archived = entityManager.createQuery(
                        "from AppointmentArchive a where a.appointmentNo = :id", AppointmentArchive.class)
                        .setParameter("id", appointmentId).getResultList();
                List<CaseManagementNote> notes = entityManager.createQuery("from CaseManagementNote n where n.uuid = :uuid", CaseManagementNote.class)
                        .setParameter("uuid", uuid).getResultList();
                if (successful) {
                    assertThat(saved.getStatus()).isEqualTo(failure.equals("refresh") ? "HS" : verify ? "tV" : "tS");
                    assertThat(saved.getLastUpdateUser()).isEqualTo("999998");
                    assertThat(archived).singleElement().satisfies(old -> {
                        assertThat(old.getStatus()).isEqualTo(failure.equals("refresh") ? "H" : "t");
                        assertThat(old.getLastUpdateUser()).isEqualTo(failure.equals("refresh") ? "concurrent" : "previous");
                    });
                    assertThat(notes).hasSize(1);
                } else {
                    assertThat(saved.getStatus()).isEqualTo("t");
                    assertThat(saved.getLastUpdateUser()).isEqualTo("previous");
                    assertThat(archived).isEmpty();
                    assertThat(notes).isEmpty();
                }
            });
        } finally {
            tx.executeWithoutResult(status -> {
                entityManager.createQuery("delete HashAudit h where h.id2 in (select cast(n.id as string) from CaseManagementNote n where n.uuid = :uuid)")
                        .setParameter("uuid", uuid).executeUpdate();
                entityManager.createQuery("delete CaseManagementNote n where n.uuid = :uuid").setParameter("uuid", uuid).executeUpdate();
                entityManager.createQuery("delete AppointmentArchive a where a.appointmentNo = :id").setParameter("id", appointmentId).executeUpdate();
                entityManager.remove(entityManager.find(Appointment.class, appointmentId));
            });
        }
    }
}
