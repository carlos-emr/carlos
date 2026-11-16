/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction/row-lock boundary; DAO/manager JDBC stand-ins isolate identifier preservation. */
@Tag("integration")
@Tag("security")
class DemographicIdentifierPreservationTransactionIntegrationTest {
    public interface Writes { DemographicTo1 updateDemographicData(DemographicTo1 data); }
    private static final class Resource extends DemographicService implements Writes {
        private final LoggedInInfo actor;
        Resource(DemographicDao records, LoggedInInfo actor) {
            super(mock(CdsLegacyProjectionService.class), records); this.actor = actor;
        }
        @Override protected LoggedInInfo getLoggedInInfo() { return actor; }
    }

    private JdbcTemplate jdbc;
    private Writes api;
    private final CountDownLatch firstUpdated = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);
    private final CountDownLatch secondLockAttempt = new CountDownLatch(1);

    @BeforeEach
    void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:identifier_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table patients (id integer primary key, sin varchar(30), hin varchar(30), first_name varchar(40))");
        jdbc.update("insert into patients values (42, ?, ?, ?)", "FAKE-SIN", "FAKE-HIN", "Original");
        var actor = new LoggedInInfo();
        var records = mock(DemographicDao.class);
        var locks = new AtomicInteger();
        when(records.findFreshForUpdate(42)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (locks.incrementAndGet() == 2) secondLockAttempt.countDown();
            var row = jdbc.queryForMap("select sin, hin from patients where id=42 for update");
            var previous = new Demographic(); previous.setDemographicNo(42);
            previous.setSin((String) row.get("SIN")); previous.setHin((String) row.get("HIN"));
            return previous;
        });
        var manager = mock(DemographicManager.class);
        doAnswer(call -> {
            Demographic patient = call.getArgument(1);
            jdbc.update("update patients set sin=?, hin=?, first_name=? where id=42",
                    patient.getSin(), patient.getHin(), patient.getFirstName());
            if ("fail".equals(patient.getFirstName())) throw new IllegalStateException("late write failure");
            if ("first".equals(patient.getFirstName())) {
                firstUpdated.countDown();
                if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("first writer was not released");
            }
            return null;
        }).when(manager).updateDemographic(eq(actor), any(Demographic.class));
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(actor, "_demographic", "u", 42)).thenReturn(true);
        var target = new Resource(records, actor);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        ReflectionTestUtils.setField(target, "demographicManager", manager);
        var factory = new ProxyFactory(target);
        factory.setInterfaces(Writes.class);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        api = (Writes) factory.getProxy();
    }

    @AfterEach void cleanup() { jdbc.execute("shutdown"); }

    private static DemographicTo1 payload(String name, String sin, String hin) {
        var data = new DemographicTo1(); data.setDemographicNo(42);
        data.setFirstName(name); data.setLastName("Patient");
        data.setDobYear("1980"); data.setDobMonth("06"); data.setDobDay("15");
        data.setSin(sin); data.setHin(hin); return data;
    }

    @Test
    void shouldPreserveUndisclosedIdentifiers_insideTheRealWriteTransaction() {
        var result = api.updateDemographicData(payload("Changed", null, null));
        assertThat(jdbc.queryForMap("select sin, hin, first_name from patients where id=42"))
                .containsEntry("SIN", "FAKE-SIN").containsEntry("HIN", "FAKE-HIN").containsEntry("FIRST_NAME", "Changed");
        assertThat(result.getSin()).isNull();
    }

    @Test
    void shouldHonorExplicitEmptyStringClears() {
        api.updateDemographicData(payload("Changed", "", ""));
        assertThat(jdbc.queryForMap("select sin, hin from patients where id=42"))
                .containsEntry("SIN", "").containsEntry("HIN", "");
    }

    @Test
    void shouldRollbackALateFailure_withoutLosingStoredIdentifiersOrOtherFields() {
        var data = payload("fail", null, null);
        assertThatThrownBy(() -> api.updateDemographicData(data)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("select sin, hin, first_name from patients where id=42"))
                .containsEntry("SIN", "FAKE-SIN").containsEntry("HIN", "FAKE-HIN").containsEntry("FIRST_NAME", "Original");
    }

    @Test
    void shouldPreserveTheLatestCommittedIdentifiers_whenAnotherWriterWasHoldingTheRow() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> first = pool.submit(() -> api.updateDemographicData(payload("first", "NEW-SIN", "NEW-HIN")));
            try {
                assertThat(firstUpdated.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = pool.submit(() -> api.updateDemographicData(payload("second", null, null)));
                assertThat(secondLockAttempt.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(second.isDone()).isFalse();
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            } finally { releaseFirst.countDown(); }
        }
        assertThat(jdbc.queryForMap("select sin, hin, first_name from patients where id=42"))
                .containsEntry("SIN", "NEW-SIN").containsEntry("HIN", "NEW-HIN").containsEntry("FIRST_NAME", "second");
    }
}
