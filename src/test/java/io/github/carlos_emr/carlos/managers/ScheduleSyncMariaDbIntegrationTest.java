/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.*;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production seek queries and transaction annotations on generated and unmodified ON/BC schemas. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ScheduleSyncMariaDbIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class ScheduleSyncMariaDbIntegrationTest {
    private static final long UPDATED = 1_000_000_000_000L;
    private static final Date START = new Date(0);
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private EntityManager em;
    private OscarAppointmentDaoImpl appointments;
    private AppointmentArchiveDaoImpl archives;
    private ScheduleManager manager;
    private SecurityInfoManager security;
    private PatientConsentManager consent;
    private LoggedInInfo actor;
    private MockedStatic<LogAction> audit;
    private JdbcTemplate jdbc;
    private final List<Integer> rawLimits = new ArrayList<>();

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM appointment"); jdbc.update("DELETE FROM appointmentArchive");
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        appointments = spy(new OscarAppointmentDaoImpl()); archives = spy(new AppointmentArchiveDaoImpl());
        ReflectionTestUtils.setField(appointments,"entityManager",em);
        ReflectionTestUtils.setField(archives,"entityManager",em);
        doAnswer(call -> { assertSnapshot(); rawLimits.add(call.getArgument(2)); return call.callRealMethod(); })
                .when(appointments).findByUpdateDateAndId(any(),anyInt(),anyInt());
        doAnswer(call -> { assertSnapshot(); rawLimits.add(call.getArgument(2)); return call.callRealMethod(); })
                .when(archives).findByUpdateDateAndId(any(),anyInt(),anyInt());
        doAnswer(call -> { assertSnapshot(); rawLimits.add(call.getArgument(5)); return call.callRealMethod(); })
                .when(appointments).findByProgramProviderDemographicDateAndId(any(),any(),any(),any(),anyInt(),anyInt());
        security = mock(SecurityInfoManager.class); consent = mock(PatientConsentManager.class);
        when(security.hasPrivilege(any(),eq("_appointment.UpdatedAfterDate"),eq("x"),isNull())).thenReturn(true);
        when(consent.filterProviderSpecificConsent(any(),anyList())).thenAnswer(call -> call.getArgument(1));
        actor = mock(LoggedInInfo.class); when(actor.getLoggedInProviderNo()).thenReturn("FAKE");
        var target = new ScheduleManagerImpl();
        ReflectionTestUtils.setField(target,"oscarAppointmentDao",appointments);
        ReflectionTestUtils.setField(target,"appointmentArchiveDao",archives);
        ReflectionTestUtils.setField(target,"securityInfoManager",security);
        ReflectionTestUtils.setField(target,"patientConsentManager",consent);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        manager = (ScheduleManager) proxy.getProxy();
        audit = mockStatic(LogAction.class); // Audit persistence is qualified separately; no fake clinical DAO.
        rawLimits.clear();
    }
    @AfterEach void closeAudit() { if (audit != null) audit.close(); }
    private void assertSnapshot() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(em.unwrap(Session.class).doReturningWork(Connection::getTransactionIsolation))
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
    }
    private void seed(int count) throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
            for (int id=1; id<=count; id++) { insert(connection,id); insertArchive(connection,id); }
        }
    }
    private void insert(Connection connection,int id) throws Exception {
        try (var sql = connection.prepareStatement("INSERT INTO appointment (appointment_no,provider_no,appointment_date,start_time,end_time,demographic_no,program_id,updatedatetime,createdatetime,status) VALUES (?, 'FAKE', '2001-09-09', '09:00:00', '09:15:00',42,5,?,?,'t')")) {
            sql.setInt(1,id); sql.setTimestamp(2,new Timestamp(UPDATED)); sql.setTimestamp(3,new Timestamp(UPDATED)); sql.executeUpdate();
        }
    }
    private void insertArchive(Connection connection,int id) throws Exception {
        try (var sql = connection.prepareStatement("INSERT INTO appointmentArchive (id,appointment_no,provider_no,appointment_date,start_time,end_time,demographic_no,program_id,updatedatetime,createdatetime,status) VALUES (?,?,'FAKE','2001-09-09','09:00:00','09:15:00',42,5,?,?,'t')")) {
            sql.setInt(1,id); sql.setInt(2,id); sql.setTimestamp(3,new Timestamp(UPDATED)); sql.setTimestamp(4,new Timestamp(UPDATED)); sql.executeUpdate();
        }
    }
    @Test void shouldPageCurrentTiesWithoutLoss_onTheNativeSchema() throws Exception {
        seed(1203); var ids = new ArrayList<Integer>(); Date position=START; int id=Integer.MAX_VALUE;
        while (true) {
            var page=manager.getAppointmentUpdatedPage(actor,position,id,199);
            page.items().forEach(row -> ids.add(row.getId()));
            if (!page.hasMore()) break;
            position=new Date(page.updatedMillis()); id=page.afterId();
        }
        assertThat(ids).containsExactlyElementsOf(IntStream.rangeClosed(1,1203).boxed().toList());
    }
    @Test void shouldPageArchiveTiesWithoutLoss_onTheNativeSchema() throws Exception {
        seed(17); var ids=new ArrayList<Integer>(); Date position=START; int id=Integer.MAX_VALUE;
        while (true) {
            var page=manager.getAppointmentArchiveUpdatedPage(actor,position,id,3);
            page.items().forEach(row -> ids.add(row.getId()));
            if (!page.hasMore()) break;
            position=new Date(page.updatedMillis()); id=page.afterId();
        }
        assertThat(ids).containsExactlyElementsOf(IntStream.rangeClosed(1,17).boxed().toList());
    }
    @Test void shouldApplyProgramProviderPatientFilters_whilePagingNativeTies() throws Exception {
        seed(9); jdbc.update("UPDATE appointment SET program_id=0 WHERE appointment_no=2");
        jdbc.update("UPDATE appointment SET program_id=6 WHERE appointment_no=3");
        jdbc.update("UPDATE appointment SET provider_no='OTHER' WHERE appointment_no=4");
        jdbc.update("UPDATE appointment SET demographic_no=43 WHERE appointment_no=5");
        var first=manager.getAppointmentsByProgramProviderDemographicPage(actor,5,"FAKE",42,START,Integer.MAX_VALUE,2);
        var second=manager.getAppointmentsByProgramProviderDemographicPage(actor,5,"FAKE",42,new Date(first.updatedMillis()),first.afterId(),8);
        assertThat(first.items()).extracting(Appointment::getId).containsExactly(1,2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.items()).extracting(Appointment::getId).containsExactly(6,7,8,9);
        assertThat(second.hasMore()).isFalse();
    }
    @Test void shouldFaultLegacyTiePages_andKeepCompleteNativeTimestampGroups() throws Exception {
        seed(3); var calendar=java.util.Calendar.getInstance(); calendar.setTime(START);
        assertThatThrownBy(() -> manager.getAppointmentUpdatedAfterDate(actor,START,2)).isInstanceOf(ScheduleSyncBoundaryException.class);
        assertThatThrownBy(() -> manager.getAppointmentArchiveUpdatedAfterDate(actor,START,2)).isInstanceOf(ScheduleSyncBoundaryException.class);
        assertThatThrownBy(() -> manager.getAppointmentsByProgramProviderDemographicDate(actor,5,"FAKE",42,calendar,2)).isInstanceOf(ScheduleSyncBoundaryException.class);
        assertThat(manager.getAppointmentUpdatedAfterDate(actor,START,3)).extracting(Appointment::getId).containsExactly(1,2,3);
        assertThat(manager.getAppointmentArchiveUpdatedAfterDate(actor,START,3)).extracting(AppointmentArchive::getId).containsExactly(1,2,3);
        assertThat(manager.getAppointmentsByProgramProviderDemographicDate(actor,5,"FAKE",42,calendar,3)).extracting(Appointment::getId).containsExactly(1,2,3);
    }
    @Test void shouldContinuePastConsentBlockedRows_withoutReturningAFalseEmptyNativePage() throws Exception {
        seed(9); when(security.hasPrivilege(any(),any(),any(),isNull())).thenReturn(false);
        when(consent.filterProviderSpecificConsent(any(),anyList())).thenAnswer(call -> {
            List<Appointment> rows=call.getArgument(1); return rows.stream().filter(row -> row.getId()>=8).toList();
        });
        var first=manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,1);
        assertThat(first.items()).extracting(Appointment::getId).containsExactly(8);
        assertThat(first.afterId()).isEqualTo(8); assertThat(first.hasMore()).isTrue();
        var second=manager.getAppointmentUpdatedPage(actor,new Date(first.updatedMillis()),first.afterId(),1);
        assertThat(second.items()).extracting(Appointment::getId).containsExactly(9); assertThat(second.hasMore()).isFalse();
    }
    @Test void shouldKeepASingleRepeatableReadSnapshot_acrossNativeBatchQueries() throws Exception {
        seed(6); when(security.hasPrivilege(any(),any(),any(),isNull())).thenReturn(false); var calls=new AtomicInteger();
        when(consent.filterProviderSpecificConsent(any(),anyList())).thenAnswer(call -> {
            if (calls.incrementAndGet()==1) {
                long reader=em.unwrap(Session.class).doReturningWork(connection -> {
                    try(var sql=connection.createStatement(); var rs=sql.executeQuery("SELECT CONNECTION_ID()")) {rs.next(); return rs.getLong(1);}
                });
                try(var writer=dataSource.getConnection(); var sql=writer.createStatement(); var rs=sql.executeQuery("SELECT CONNECTION_ID()")) {
                    rs.next(); assertThat(rs.getLong(1)).isNotEqualTo(reader); assertThat(writer.getAutoCommit()).isTrue(); insert(writer,7);
                }
            }
            List<Appointment> rows=call.getArgument(1); return rows.stream().filter(row -> row.getId()==7).toList();
        });
        assertThat(manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,1).items()).isEmpty();
        assertThat(calls.get()).isGreaterThan(1);
        assertThat(manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,1).items()).extracting(Appointment::getId).containsExactly(7);
    }
    @Test void shouldPropagateASecondNativeQueryFailure_withoutReturningAPartialPage() throws Exception {
        seed(4); var calls=new AtomicInteger();
        doAnswer(call -> {
            assertSnapshot(); if (calls.incrementAndGet()==2) throw new PersistenceException("Synthetic native query failure");
            return call.callRealMethod();
        }).when(appointments).findByUpdateDateAndId(any(),anyInt(),anyInt());
        when(security.hasPrivilege(any(),any(),any(),isNull())).thenReturn(false);
        when(consent.filterProviderSpecificConsent(any(),anyList())).thenAnswer(call -> {
            List<Appointment> rows=call.getArgument(1); return rows.stream().filter(row -> row.getId()==1).toList();
        });
        assertThatThrownBy(() -> manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,1)).isInstanceOf(PersistenceException.class);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void shouldRetainTheDaoHardPageLimit_atMaximumNativePageSize() throws Exception {
        seed(5001); var page=manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,5000);
        assertThat(page.items()).hasSize(5000); assertThat(page.afterId()).isEqualTo(5000); assertThat(page.hasMore()).isTrue();
        assertThat(rawLimits).isNotEmpty().allMatch(limit -> limit<=500);
        assertThatThrownBy(() -> manager.getAppointmentUpdatedAfterDate(actor,START,5000)).isInstanceOf(ScheduleSyncBoundaryException.class);
    }
    @Test void shouldRequireReconciliationForLateWritesBehindAnAlreadyReturnedCursor() throws Exception {
        try(var writer=dataSource.getConnection()) {
            insert(writer,2); insert(writer,3); writer.setAutoCommit(false); insert(writer,1);
            var first=manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,1);
            assertThat(first.items()).extracting(Appointment::getId).containsExactly(2); writer.commit();
            var second=manager.getAppointmentUpdatedPage(actor,new Date(first.updatedMillis()),first.afterId(),1);
            assertThat(second.items()).extracting(Appointment::getId).containsExactly(3);
            assertThat(manager.getAppointmentUpdatedPage(actor,START,Integer.MAX_VALUE,3).items()).extracting(Appointment::getId).containsExactly(1,2,3);
        }
    }
    @Configuration static class Config {
        @Bean(destroyMethod="close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {return new IsolatedJpaTestDatabase("schedule_sync");}
        @Bean DataSource dataSource(IsolatedJpaTestDatabase database) {return database.dataSource();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source,IsolatedJpaTestDatabase database) {
            var bean=new LocalContainerEntityManagerFactoryBean(); bean.setDataSource(source);
            bean.setManagedTypes(PersistenceManagedTypes.of(Appointment.class.getName(),AppointmentArchive.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); bean.setJpaPropertyMap(database.jpaProperties()); return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {return new JpaTransactionManager(factory);}
    }
}
