/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Header-less (timestamp-only) schedule synchronization through the production seek queries and
 * the manager's transaction annotations, on an isolated database owned by this test.
 *
 * <p>The caller is modelled on an unmodified SOAP client: it sends only an exclusive timestamp
 * and a page size, and bounds its next request by the newest update timestamp it has seen. Many
 * rows share an update second (the column has no fractional seconds), so page boundaries fall
 * inside such groups. The client must neither be refused nor lose the rest of a group.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ScheduleLegacySyncIntegrationTest.Config.class)
@Tag("integration")
@Tag("schedule")
@Tag("read")
@DisplayName("Header-less schedule synchronization over the production queries")
class ScheduleLegacySyncIntegrationTest {

    private static final long FIRST_SECOND = 1_000_000_000_000L;
    private static final Date START = new Date(0);

    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;

    private ScheduleManager manager;
    private LoggedInInfo actor;
    private MockedStatic<LogAction> audit;

    @BeforeEach
    void setup() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM appointment");
        jdbc.update("DELETE FROM appointmentArchive");
        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        OscarAppointmentDaoImpl appointments = new OscarAppointmentDaoImpl();
        AppointmentArchiveDaoImpl archives = new AppointmentArchiveDaoImpl();
        ReflectionTestUtils.setField(appointments, "entityManager", em);
        ReflectionTestUtils.setField(archives, "entityManager", em);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        PatientConsentManager consent = mock(PatientConsentManager.class);
        when(security.hasPrivilege(any(), eq("_appointment.UpdatedAfterDate"), eq("x"), isNull())).thenReturn(true);
        when(consent.filterProviderSpecificConsent(any(), anyList())).thenAnswer(call -> call.getArgument(1));
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("FAKE");
        ScheduleManagerImpl target = new ScheduleManagerImpl();
        ReflectionTestUtils.setField(target, "oscarAppointmentDao", appointments);
        ReflectionTestUtils.setField(target, "appointmentArchiveDao", archives);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        ReflectionTestUtils.setField(target, "patientConsentManager", consent);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        manager = (ScheduleManager) proxy.getProxy();
        // The audit trail has its own tests; this fixture has no audit table.
        audit = mockStatic(LogAction.class);
    }

    @AfterEach
    void closeAudit() {
        if (audit != null) audit.close();
    }

    /** Inserts current and archived rows {@code firstId..lastId}, all updated in one whole second. */
    private void seed(int firstId, int lastId, int second) throws Exception {
        Timestamp updated = new Timestamp(FIRST_SECOND + second * 1000L);
        try (Connection connection = dataSource.getConnection();
             var current = connection.prepareStatement("INSERT INTO appointment (appointment_no,provider_no,"
                     + "appointment_date,start_time,end_time,demographic_no,program_id,updatedatetime,createdatetime,status)"
                     + " VALUES (?, 'FAKE', '2001-09-09', '09:00:00', '09:15:00', 42, 5, ?, ?, 't')");
             var archived = connection.prepareStatement("INSERT INTO appointmentArchive (id,appointment_no,provider_no,"
                     + "appointment_date,start_time,end_time,demographic_no,program_id,updatedatetime,createdatetime,status)"
                     + " VALUES (?, ?, 'FAKE', '2001-09-09', '09:00:00', '09:15:00', 42, 5, ?, ?, 't')")) {
            for (int id = firstId; id <= lastId; id++) {
                current.setInt(1, id);
                current.setTimestamp(2, updated);
                current.setTimestamp(3, updated);
                current.addBatch();
                archived.setInt(1, id);
                archived.setInt(2, id);
                archived.setTimestamp(3, updated);
                archived.setTimestamp(4, updated);
                archived.addBatch();
            }
            current.executeBatch();
            archived.executeBatch();
        }
    }

    /** One row, four sharing a second, one more, two sharing a second, and a last one. */
    private void seedGroupsThatStraddlePageBoundaries() throws Exception {
        seed(1, 1, 1);
        seed(2, 5, 2);
        seed(6, 6, 3);
        seed(7, 8, 4);
        seed(9, 9, 5);
    }

    /**
     * An unmodified client: every request carries only the newest update timestamp seen so far and
     * the page size, and it stops at the first empty page.
     */
    private <T> List<Integer> synchronize(BiFunction<Date, Integer, List<T>> request, Function<T, Integer> id,
            Function<T, Date> updated, int pageSize, List<Integer> pageSizes) {
        List<Integer> seen = new ArrayList<>();
        Date after = START;
        for (int requests = 0; requests < 50; requests++) {
            List<T> page = request.apply(after, pageSize);
            if (page.isEmpty()) return seen;
            pageSizes.add(page.size());
            Date newest = after;
            for (T row : page) {
                seen.add(id.apply(row));
                if (updated.apply(row).after(newest)) newest = new Date(updated.apply(row).getTime());
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
    @DisplayName("should lose no current appointment across consecutive timestamp-only pages")
    void shouldLoseNoCurrentAppointment_acrossConsecutiveTimestampOnlyPages() throws Exception {
        seedGroupsThatStraddlePageBoundaries();
        List<Integer> pageSizes = new ArrayList<>();

        List<Integer> seen = synchronize((after, size) -> manager.getAppointmentUpdatedAfterDate(actor, after, size),
                Appointment::getId, Appointment::getUpdateDateTime, 2, pageSizes);

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
        // 1,2 then the rest of 2's second; 6,7 then the rest of 7's second; then the last row.
        assertThat(pageSizes).containsExactly(5, 3, 1);
    }

    @Test
    @DisplayName("should lose no archived appointment across consecutive timestamp-only pages")
    void shouldLoseNoArchivedAppointment_acrossConsecutiveTimestampOnlyPages() throws Exception {
        seedGroupsThatStraddlePageBoundaries();

        List<Integer> seen = synchronize(
                (after, size) -> manager.getAppointmentArchiveUpdatedAfterDate(actor, after, size),
                AppointmentArchive::getId, AppointmentArchive::getUpdateDateTime, 2, new ArrayList<>());

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
    }

    @Test
    @DisplayName("should lose no filtered appointment across consecutive timestamp-only pages")
    void shouldLoseNoFilteredAppointment_acrossConsecutiveTimestampOnlyPages() throws Exception {
        seedGroupsThatStraddlePageBoundaries();

        List<Integer> seen = synchronize((after, size) -> {
            Calendar bound = Calendar.getInstance();
            bound.setTime(after);
            return manager.getAppointmentsByProgramProviderDemographicDate(actor, 5, "FAKE", 42, bound, size);
        }, Appointment::getId, Appointment::getUpdateDateTime, 2, new ArrayList<>());

        assertThat(seen).containsExactlyElementsOf(ids(1, 9));
    }

    @Test
    @DisplayName("should return the requested page unchanged when the boundary shares no timestamp")
    void shouldReturnTheRequestedPage_whenTheBoundarySharesNoTimestamp() throws Exception {
        for (int id = 1; id <= 5; id++) seed(id, id, id);

        assertThat(manager.getAppointmentUpdatedAfterDate(actor, START, 2))
                .extracting(Appointment::getId).containsExactly(1, 2);
        assertThat(manager.getAppointmentUpdatedAfterDate(actor, START, 0)).isEmpty();
    }

    @Test
    @DisplayName("should return the truncated page without a fault when one second holds more rows than the hard limit")
    void shouldReturnTheTruncatedPage_whenOneSecondHoldsMoreRowsThanTheHardLimit() throws Exception {
        // 5,003 rows written in one second: no response within the 5,000-row limit can hold them all.
        seed(1, 5003, 1);
        seed(5004, 5004, 2);

        List<Appointment> page = manager.getAppointmentUpdatedAfterDate(actor, START, 3);

        // The 2026.08.0-beta6 answer: exactly the requested rows. Nothing is refused.
        assertThat(page).extracting(Appointment::getId).containsExactly(1, 2, 3);
        Date bound = page.getLast().getUpdateDateTime();
        assertThat(manager.getAppointmentUpdatedAfterDate(actor, bound, 3))
                .extracting(Appointment::getId).containsExactly(5004);
    }

    @Configuration
    static class Config {
        @Bean(destroyMethod = "close")
        IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("legacy_sched_sync");
        }

        @Bean
        DataSource dataSource(IsolatedJpaTestDatabase database) {
            return database.dataSource();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, IsolatedJpaTestDatabase database) {
            LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
            bean.setDataSource(source);
            bean.setManagedTypes(PersistenceManagedTypes.of(Appointment.class.getName(), AppointmentArchive.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setJpaPropertyMap(database.jpaProperties());
            return bean;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
