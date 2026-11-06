/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentDaySheetRow;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Commits real appointment/archive rows and proves a later archive failure rolls the report back. */
@Tag("integration")
@Tag("appointment")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentDaySheetServiceIntegrationTest extends CarlosTestBase {
    private static final String DAY = "2026-11-06";
    private static final String PROVIDER = "999998";
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private OscarAppointmentDao appointments;
    @Autowired private AppointmentArchiveDao archives;
    private final List<Integer> ids = new ArrayList<>();
    private TransactionTemplate tx;
    private String ownedProvider;

    @BeforeEach
    void createAppointments() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (int i = 0; i < 2; i++) {
                Appointment appointment = new Appointment();
                appointment.setProviderNo(PROVIDER);
                appointment.setAppointmentDate(Date.valueOf(DAY));
                appointment.setStartTime(Time.valueOf("09:00:00"));
                appointment.setEndTime(Time.valueOf("09:15:00"));
                appointment.setName("DaySheetSynthetic");
                appointment.setStatus("t");
                appointment.setLastUpdateUser("prior");
                entityManager.persist(appointment);
                entityManager.flush();
                ids.add(appointment.getId());
            }
        });
    }

    @AfterEach
    void removeOwnedRows() {
        if (ids.isEmpty()) return;
        tx.executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM AppointmentArchive a WHERE a.appointmentNo IN :ids")
                    .setParameter("ids", ids).executeUpdate();
            entityManager.createQuery("DELETE FROM Appointment a WHERE a.id IN :ids")
                    .setParameter("ids", ids).executeUpdate();
            if (ownedProvider != null) {
                entityManager.createNativeQuery("DELETE FROM provider WHERE provider_no=:provider")
                        .setParameter("provider", ownedProvider).executeUpdate();
            }
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
    void shouldReadPhysicalIdsAndEditVersions_beforeAcknowledgingNativeDaySheetRows() {
        String provider = "DS" + UUID.randomUUID().toString().substring(0, 4);
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("INSERT INTO provider (provider_no,first_name,last_name,ohip_no,"
                            + "billing_no,provider_type,status,lastUpdateDate)"
                            + " VALUES (:provider,'Synthetic','DaySheet','','','doctor','1',CURRENT_TIMESTAMP)")
                    .setParameter("provider", provider).executeUpdate();
            entityManager.createNativeQuery("UPDATE appointment SET provider_no=:provider WHERE appointment_no IN (:ids)")
                    .setParameter("provider", provider).setParameter("ids", ids).executeUpdate();
            entityManager.createNativeQuery("UPDATE appointment SET updatedatetime=NULL WHERE appointment_no=:id")
                    .setParameter("id", ids.get(1)).executeUpdate();
        });
        ownedProvider = provider;

        List<AppointmentDaySheetRow> rows = appointments.findDaySheet(DAY, DAY, "00:00:00", "24:00:00",
                provider, true, "start_time");

        assertThat(rows).extracting(AppointmentDaySheetRow::appointmentNo).containsExactlyInAnyOrderElementsOf(ids);
        for (AppointmentDaySheetRow row : rows) {
            String expectedVersion = tx.execute(status -> appointments.find(row.appointmentNo()).getEditVersion());
            assertThat(row.editVersion()).isEqualTo(expectedVersion);
            assertThat(row.providerNo()).isEqualTo(provider);
            assertThat(row.date()).isEqualTo(DAY);
            assertThat(row.startTime()).isEqualTo("09:00:00");
            assertThat(row.name()).isEqualTo("DaySheetSynthetic");
        }
        assertThat(rows).filteredOn(row -> row.appointmentNo().equals(ids.get(1)))
                .extracting(AppointmentDaySheetRow::editVersion).containsExactly("unversioned");

        assertThat(service(archives).prepare(rows, true, null, null, false, provider)).containsExactlyElementsOf(rows);
        assertThat(statuses()).containsExactly("T", "T");
        assertThat(archivedStatuses()).containsExactly("t", "t");
        assertThat(appointments.findDaySheet(DAY, DAY, "00:00:00", "24:00:00", provider, true, "start_time")).isEmpty();
        assertThat(appointments.findDaySheet(DAY, DAY, "00:00:00", "24:00:00", provider, false, "start_time"))
                .extracting(AppointmentDaySheetRow::appointmentNo).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void shouldCommitAcknowledgmentsAndOriginalVersions_whenEveryArchiveSucceeds() {
        List<AppointmentDaySheetRow> rows = List.of(row(ids.get(1)), row(ids.get(0)));
        assertThat(service(archives).prepare(rows, true, null, null, false, PROVIDER)).containsExactlyElementsOf(rows);

        assertThat(statuses()).containsExactly("T", "T");
        assertThat(archivedStatuses()).containsExactly("t", "t");
        List<String> archivedUsers = tx.execute(status -> entityManager.createQuery(
                        "SELECT a.lastUpdateUser FROM AppointmentArchive a WHERE a.appointmentNo IN :ids ORDER BY a.appointmentNo",
                        String.class).setParameter("ids", ids).getResultList());
        assertThat(archivedUsers).containsExactly("prior", "prior");
    }

    @Test
    void shouldRollBackEarlierAcknowledgmentAndBothArchives_whenLaterArchiveFails() {
        AppointmentArchiveDao failing = mock(AppointmentArchiveDao.class, delegatesTo(archives));
        doAnswer(call -> {
            Appointment appointment = call.getArgument(0);
            var archived = archives.archiveAppointment(appointment);
            entityManager.flush();
            if (appointment.getId().equals(ids.get(1))) {
                assertThat(statuses()).containsExactly("T", "t");
                throw new IllegalStateException("later archive failed");
            }
            return archived;
        }).when(failing).archiveAppointment(any());

        assertThatThrownBy(() -> service(failing).prepare(List.of(row(ids.get(1)), row(ids.get(0))),
                true, null, null, false, PROVIDER))
                .isInstanceOf(IllegalStateException.class).hasMessage("later archive failed");

        assertThat(statuses()).containsExactly("t", "t");
        assertThat(archivedStatuses()).isEmpty();
    }

    private AppointmentDaySheetService service(AppointmentArchiveDao archiveDao) {
        ProxyFactory proxy = new ProxyFactory(new AppointmentDaySheetService(appointments, archiveDao));
        proxy.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        return (AppointmentDaySheetService) proxy.getProxy();
    }

    private List<String> statuses() {
        return tx.execute(status -> entityManager.createQuery(
                "SELECT a.status FROM Appointment a WHERE a.id IN :ids ORDER BY a.id", String.class)
                .setParameter("ids", ids).getResultList());
    }

    private List<String> archivedStatuses() {
        return tx.execute(status -> entityManager.createQuery(
                "SELECT a.status FROM AppointmentArchive a WHERE a.appointmentNo IN :ids ORDER BY a.appointmentNo", String.class)
                .setParameter("ids", ids).getResultList());
    }

    private AppointmentDaySheetRow row(int id) {
        String version = tx.execute(status -> appointments.find(id).getEditVersion());
        return new AppointmentDaySheetRow(null, null, DAY, PROVIDER, null, null, null, null, null,
                null, null, null, null, null, null, null, "NR", null, null, null, id, version);
    }
}
