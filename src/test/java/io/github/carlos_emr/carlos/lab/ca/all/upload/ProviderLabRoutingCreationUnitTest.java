/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ForwardingRules;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("Lab delivery and acknowledgement coordination")
class ProviderLabRoutingCreationUnitTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PRIVATE_INVALID_ID", "2147483648", " ", "170\nPRIVATE_INVALID_ID"})
    @DisplayName("should reject malformed ids through both legacy string overloads before routing")
    void shouldRejectInvalidIdentifier_whenLegacyStringRouteIsCalled(String id) {
        var router = mock(ProviderLabRouting.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertThatThrownBy(() -> router.route(id, "999998", "HL7"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessage("Invalid numeric lab identifier").hasNoCause();
        var connection = mock(java.sql.Connection.class);
        assertThatThrownBy(() -> router.route(id, "999998", connection, "HL7"))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessage("Invalid numeric lab identifier").hasNoCause();
        verifyNoInteractions(connection);
        verify(router, never()).routeMagic(anyInt(), anyString(), anyString());
    }

    private static final String LOCK_SQL = "INSERT INTO providerLabRoutingLock(lab_no) VALUES(?) ON DUPLICATE KEY UPDATE lab_no=VALUES(lab_no)";

    private static class Fixture implements AutoCloseable {
        final JdbcTemplate jdbc;
        final PlatformTransactionManager manager;
        final ProviderLabRoutingDao dao = mock(ProviderLabRoutingDao.class);
        Fixture() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:lab-delivery-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            jdbc = new JdbcTemplate(source);
            manager = new DataSourceTransactionManager(source);
            jdbc.execute("CREATE TABLE providerLabRoutingLock(lab_no INT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE routing(lab_no INT, provider_no VARCHAR(20), lab_type VARCHAR(20), status VARCHAR(1))");
            doAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isEqualTo(
                        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                jdbc.update(LOCK_SQL, call.getArgument(0, Integer.class));
                return null;
            }).when(dao).lockRoutingReport(anyInt());
            when(dao.findRoutingForUpdate(anyInt(), anyString(), anyString())).thenAnswer(call ->
                    jdbc.query("SELECT status FROM routing WHERE lab_no=? AND lab_type=? AND provider_no=? FOR UPDATE",
                            (row, index) -> {
                                var routing = new ProviderLabRoutingModel();
                                routing.setStatus(row.getString(1));
                                return routing;
                            }, call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            doAnswer(call -> {
                ProviderLabRoutingModel row = call.getArgument(0);
                jdbc.update("INSERT INTO routing VALUES(?,?,?,?)", row.getLabNo(), row.getProviderNo(), row.getLabType(), row.getStatus());
                return null;
            }).when(dao).persist(any(ProviderLabRoutingModel.class));
        }
        @Override
        public void close() {
            jdbc.execute("SHUTDOWN");
        }

        void route(int entrypoint, boolean forwardingCycle) throws Exception {
            try (var spring = mockStatic(SpringUtils.class);
                 var rules = mockConstruction(ForwardingRules.class, (mock, context) -> {
                     when(mock.getStatus(anyString())).thenReturn("N");
                     when(mock.getProviders(anyString())).thenAnswer(call -> {
                         var destinations = new ArrayList<ArrayList<String>>();
                         if (forwardingCycle) destinations.add(new ArrayList<>(List.of("999998".equals(call.getArgument(0)) ? "111" : "999998")));
                         return destinations;
                     });
                 })) {
                spring.when(() -> SpringUtils.getBean(ProviderLabRoutingDao.class)).thenReturn(dao);
                spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(manager);
                var router = new ProviderLabRouting();
                switch (entrypoint) {
                    case 0 -> router.routeMagic(170, "999998", "HL7");
                    case 1 -> router.route("170", "999998", "HL7");
                    case 2 -> {
                        var connection = mock(java.sql.Connection.class);
                        router.route("170", "999998", connection, "HL7");
                        verifyNoInteractions(connection);
                    }
                    default -> throw new IllegalArgumentException("Unknown fixture entrypoint");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("should preserve acknowledged rows when normal or legacy delivery races their transaction")
    void shouldPreserveAcknowledgement_whenDeliveryWaitsForCommit(int entrypoint) throws Exception {
        try (var fixture = new Fixture()) {
            var acknowledgementReady = new CountDownLatch(1);
            var releaseAcknowledgement = new CountDownLatch(1);
            var deliveryAttempted = new CountDownLatch(1);
            doAnswer(call -> {
                deliveryAttempted.countDown();
                fixture.jdbc.update(LOCK_SQL, call.getArgument(0, Integer.class));
                return null;
            }).when(fixture.dao).lockRoutingReport(anyInt());
            var workers = Executors.newFixedThreadPool(2);
            try {
                var acknowledged = workers.submit(() -> new TransactionTemplate(fixture.manager).executeWithoutResult(status -> {
                    fixture.jdbc.update(LOCK_SQL, 170);
                    fixture.jdbc.update("INSERT INTO routing VALUES(170,'999998','HL7','A')");
                    acknowledgementReady.countDown();
                    try { assertThat(releaseAcknowledgement.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                }));
                assertThat(acknowledgementReady.await(5, TimeUnit.SECONDS)).isTrue();
                var delivered = workers.submit(() -> { fixture.route(entrypoint, false); return true; });
                assertThat(deliveryAttempted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> delivered.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releaseAcknowledgement.countDown();
                acknowledged.get(5, TimeUnit.SECONDS);
                assertThat(delivered.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(fixture.jdbc.queryForList("SELECT status FROM routing", String.class)).containsExactly("A");
                verify(fixture.dao, never()).persist(any());
                verify(fixture.dao, never()).merge(any());
            } finally {
                releaseAcknowledgement.countDown();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "F", "N"})
    @DisplayName("should preserve every existing clinical status even when another provider has a NEW row")
    void shouldNotReopenExistingRouting_whenDeliveredAgain(String status) throws Exception {
        try (var fixture = new Fixture()) {
            fixture.jdbc.update("INSERT INTO routing VALUES(170,'999998','HL7',?)", status);
            fixture.jdbc.update("INSERT INTO routing VALUES(170,'0','HL7','N')");
            fixture.route(0, false);
            assertThat(fixture.jdbc.queryForObject("SELECT status FROM routing WHERE provider_no='999998'", String.class)).isEqualTo(status);
            verify(fixture.dao, never()).persist(any());
            verify(fixture.dao, never()).merge(any());
        }
    }

    @Test
    @DisplayName("should roll back the original routing when creation of a forwarded routing fails")
    void shouldRollbackAllRouting_whenForwardingFails() {
        try (var fixture = new Fixture()) {
            doAnswer(call -> {
                ProviderLabRoutingModel row = call.getArgument(0);
                if ("111".equals(row.getProviderNo())) throw new IllegalStateException("fixture persistence failure");
                fixture.jdbc.update("INSERT INTO routing VALUES(?,?,?,?)", row.getLabNo(), row.getProviderNo(), row.getLabType(), row.getStatus());
                return null;
            }).when(fixture.dao).persist(any(ProviderLabRoutingModel.class));
            assertThatThrownBy(() -> fixture.route(0, true)).isInstanceOf(IllegalStateException.class);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM routing", Integer.class)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("should create each forwarded routing once and terminate cycles within one transaction")
    void shouldRouteForwardingCycle_oncePerProvider(int entrypoint) throws Exception {
        try (var fixture = new Fixture()) {
            fixture.route(entrypoint, true);
            assertThat(fixture.jdbc.queryForList("SELECT provider_no FROM routing ORDER BY provider_no", String.class))
                    .containsExactly("111", "999998");
            verify(fixture.dao).lockRoutingReport(170);
            verify(fixture.dao, times(2)).persist(any());
        }
    }
}
