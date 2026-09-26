/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.GroupMembersDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.GroupsDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Groups;
import io.github.carlos_emr.carlos.commn.model.GroupMembers;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.h2.tools.RunScript;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class MessengerMembershipConcurrencyIntegrationTest {
    private static final Path MIGRATION = Path.of("database/mysql/migration/common/V1.0.36__serialize_messenger_membership_changes.sql");

    private static class Fixture implements AutoCloseable {
        final String url = "jdbc:h2:mem:messenger-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        final SessionFactory factory;
        final GroupMembersDaoImpl dao = spy(new GroupMembersDaoImpl());
        final MessengerGroupManager manager;
        final LoggedInInfo info = mock(LoggedInInfo.class);

        Fixture() throws Exception {
            factory = new Configuration().addAnnotatedClass(GroupMembers.class).addAnnotatedClass(Groups.class)
                    .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                    .setProperty("hibernate.connection.pool_size", "4")
                    .buildSessionFactory();
            try (var connection = DriverManager.getConnection(url)) {
                // Repeatability matters if deployment is interrupted after the DDL commits.
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (var reader = Files.newBufferedReader(MIGRATION)) { RunScript.execute(connection, reader); }
                }
            }
            ReflectionTestUtils.setField(dao, "entityManager", SharedEntityManagerCreator.createSharedEntityManager(factory));
            var target = new MessengerGroupManager();
            ReflectionTestUtils.setField(target, "groupMembersDao", dao);
            var groups = new GroupsDaoImpl();
            ReflectionTestUtils.setField(groups, "entityManager", SharedEntityManagerCreator.createSharedEntityManager(factory));
            ReflectionTestUtils.setField(target, "groupsDao", groups);
            try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
                statement.execute("INSERT INTO groups_tbl(groupID,parentID,groupDesc) VALUES(7,0,'One'),(8,0,'Two'),(13,0,'Rejected')");
            }
            var security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(any(), eq("_admin"), any(), isNull())).thenReturn(true);
            ReflectionTestUtils.setField(target, "securityInfoManager", security);
            var proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(new JpaTransactionManager(factory), new AnnotationTransactionAttributeSource()));
            manager = (MessengerGroupManager) proxy.getProxy();
        }

        MessengerGroupManager.AddMemberResult add(int group) {
            return manager.addMemberIfAbsent(info, new ContactIdentifier("101-0-145"), group);
        }

        int count(int group) throws Exception {
            try (var connection = DriverManager.getConnection(url);
                 var query = connection.prepareStatement("SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=?")) {
                query.setInt(1, group);
                try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1); }
            }
        }

        @Override
        public void close() throws Exception {
            factory.close();
            try (var connection = DriverManager.getConnection(url); var query = connection.createStatement()) {
                query.execute("SHUTDOWN");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 8})
    void shouldSerializeFirstInsert_andKeepOneRegistryAcrossGroups(int secondGroup) throws Exception {
        try (var fixture = new Fixture()) {
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var attempted = new CountDownLatch(1);
            var first = new java.util.concurrent.atomic.AtomicBoolean(true);
            doAnswer(call -> {
                boolean hold = first.getAndSet(false);
                if (!hold) attempted.countDown();
                call.callRealMethod();
                if (hold) {
                    locked.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                }
                return null;
            }).when(fixture.dao).lockMembershipChanges();
            var workers = Executors.newFixedThreadPool(2);
            try {
                var firstAdd = workers.submit(() -> fixture.add(7));
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                var secondAdd = workers.submit(() -> fixture.add(secondGroup));
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> secondAdd.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(firstAdd.get(5, TimeUnit.SECONDS).created()).isTrue();
                assertThat(secondAdd.get(5, TimeUnit.SECONDS).created()).isEqualTo(secondGroup != 7);
                assertThat(fixture.count(0)).isEqualTo(1);
                assertThat(fixture.count(7)).isEqualTo(1);
                assertThat(fixture.count(secondGroup)).isEqualTo(1);
            } finally {
                release.countDown();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void shouldRollbackRegistry_whenGroupInsertFails() throws Exception {
        try (var fixture = new Fixture()) {
            try (var connection = DriverManager.getConnection(fixture.url); var query = connection.createStatement()) {
                query.execute("ALTER TABLE groupMembers_tbl ADD CONSTRAINT reject_fixture_group CHECK(groupID <> 13)");
            }
            assertThatThrownBy(() -> fixture.add(13)).isInstanceOf(RuntimeException.class);
            assertThat(fixture.count(0)).isZero();
            assertThat(fixture.count(13)).isZero();
            assertThat(fixture.add(7).created()).isTrue();
            assertThat(fixture.count(0)).isEqualTo(1);
        }
    }
}
