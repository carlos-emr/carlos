/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogActionTestHooks;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.TicklerWebService;
import io.github.carlos_emr.carlos.webserv.rest.exceptionMapping.GeneralExceptionMapper;

/**
 * The REST access audit row must be committed independently of the resource's own transaction.
 *
 * <p>Drives {@link TicklerWebService} over CXF through the proxy Spring builds for it in
 * production: the real auto-proxy creator, the real annotation-driven transaction advisor and the
 * real {@link WebServiceLoggingAdvice}, with the transaction advisor ordered as
 * {@code applicationContext.xml} declares it. Only the managers behind the resource are mocked; the
 * audit rows are real rows in the {@code log} table.</p>
 *
 * <p>The assertions are on what the transaction synchronization shows at the moment the audit row
 * is written, and on the rows left afterwards. They do not wait for a database to refuse the
 * write: H2 accepts an {@code INSERT} on a read-only connection, where MariaDB answers
 * "Cannot execute statement in a READ ONLY transaction" and the request fails with HTTP 500.</p>
 */
@Tag("integration")
@Tag("mariadb-native")
@DisplayName("REST audit rows and the resource transaction")
class WebServiceLoggingAdviceTransactionIntegrationTest extends CarlosRestTestBase {

    private static final String TX_NAMESPACE = "http://www.springframework.org/schema/tx";

    private static final String READ_AUDIT = "REST WS: TicklerWebService.getMyTicklers";
    private static final String WRITE_AUDIT = "REST WS: TicklerWebService.completeTicklers";
    private static final String WRITE_FAILURE_AUDIT = "REST WS: FAILURE: TicklerWebService.completeTicklers";
    /** A row the resource writes inside its own transaction, to show whether that transaction committed. */
    private static final String RESOURCE_WRITE = "synthetic resource write";

    /** What the transaction synchronization showed at one point of a request. */
    private record TransactionState(boolean active, boolean readOnly) {
        static TransactionState current() {
            return new TransactionState(TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        }
    }

    /** One audit entry handed to the audit DAO, and the caller's transaction at that moment. */
    private record AuditWrite(String action, TransactionState callerTransaction) {
    }

    private static final TransactionState NO_TRANSACTION = new TransactionState(false, false);

    @Mock
    private TicklerManager ticklerManager;
    @Mock
    private SecurityInfoManager securityInfoManager;

    /**
     * The {@code order} of the transaction advisor in the context under test: what production
     * declares, unless a nested class replaces it before the context is built.
     */
    private String transactionAdvisorOrder = productionTransactionAdvisorOrder();

    private IsolatedJpaTestDatabase database;
    private GenericApplicationContext context;
    private OscarLogDao auditDao;
    private JdbcTemplate jdbc;

    private final List<AuditWrite> auditWrites = new ArrayList<>();
    private final List<TransactionState> resourceTransactions = new ArrayList<>();
    /** When set, the database write of a REST audit row fails with this exception. */
    private RuntimeException auditStoreFailure;

    /**
     * The {@code order} attribute of the {@code <tx:annotation-driven>} that configures the
     * production transaction advisor, or {@code null} when it declares none.
     *
     * <p>That is the declaration in {@code applicationContext.xml}: Spring registers the advisor for
     * the first such element it parses and ignores the attributes of any later one, and
     * {@code applicationContext.xml} declares its own before importing {@code spring_jpa.xml}.</p>
     */
    static String productionTransactionAdvisorOrder() {
        try (InputStream stream = new ClassPathResource("applicationContext.xml").getInputStream()) {
            var parser = XmlUtils.createSecureDocumentBuilderFactory();
            parser.setNamespaceAware(true);
            NodeList declarations = parser.newDocumentBuilder().parse(stream)
                    .getElementsByTagNameNS(TX_NAMESPACE, "annotation-driven");
            assertThat(declarations.getLength()).as("tx:annotation-driven in applicationContext.xml").isEqualTo(1);
            Element declaration = (Element) declarations.item(0);
            return declaration.hasAttribute("order") ? declaration.getAttribute("order") : null;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the production transaction advisor order", e);
        }
    }

    @Override
    protected Object getServiceBean() {
        try {
            database = new IsolatedJpaTestDatabase("rest_audit_tx");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create the audit test database", e);
        }
        var entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(database.dataSource());
        entityManagerFactory.setManagedTypes(PersistenceManagedTypes.of(OscarLog.class.getName()));
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactory.setJpaPropertyMap(database.jpaProperties());

        var resource = new TicklerWebService();
        injectDependency(resource, "ticklerManager", ticklerManager);
        injectDependency(resource, "securityInfoManager", securityInfoManager);

        context = new GenericApplicationContext();
        new XmlBeanDefinitionReader(context).loadBeanDefinitions(
                new ByteArrayResource(adviceInfrastructure().getBytes(StandardCharsets.UTF_8)));
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class,
                () -> entityManagerFactory);
        context.registerBean("transactionManager", JpaTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean("oscarLogDao", OscarLogDaoImpl.class, () -> {
            var dao = new OscarLogDaoImpl();
            injectDependency(dao, "entityManager", failingOnDemand(SharedEntityManagerCreator
                    .createSharedEntityManager(context.getBean(EntityManagerFactory.class))));
            return dao;
        });
        context.registerBean(WebServiceLoggingAdvice.class);
        context.registerBean("ticklerWebService", TicklerWebService.class, () -> resource);
        context.refresh();

        auditDao = context.getBean(OscarLogDao.class);
        jdbc = new JdbcTemplate(database.dataSource());
        return context.getBean(TicklerWebService.class);
    }

    /** The auto-proxy and transaction declarations of {@code applicationContext.xml}. */
    private String adviceInfrastructure() {
        String order = transactionAdvisorOrder == null ? "" : " order=\"" + transactionAdvisorOrder + "\"";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:aop="http://www.springframework.org/schema/aop"
                       xmlns:tx="http://www.springframework.org/schema/tx"
                       xsi:schemaLocation="
                           http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd
                           http://www.springframework.org/schema/aop http://www.springframework.org/schema/aop/spring-aop.xsd
                           http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx.xsd">
                    <aop:aspectj-autoproxy proxy-target-class="true"/>
                    <tx:annotation-driven proxy-target-class="true"%s/>
                </beans>
                """.formatted(order);
    }

    /** An entity manager whose insert of a REST audit row fails while {@link #auditStoreFailure} is set. */
    private EntityManager failingOnDemand(EntityManager target) {
        return (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {EntityManager.class},
                (proxy, method, arguments) -> {
                    if (auditStoreFailure != null && "persist".equals(method.getName())
                            && arguments[0] instanceof OscarLog entry && entry.getAction().startsWith("REST WS")) {
                        throw auditStoreFailure;
                    }
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    protected List<Object> additionalServerProviders() {
        // As registered on both production REST servers: an escaping exception is an HTTP 500.
        return List.of(new GeneralExceptionMapper());
    }

    @BeforeEach
    void routeAuditsToTheTestDatabase() {
        // The unit base silences LogAction; this suite needs the real audit path.
        logActionMock.close();
        logActionMock = null;
        Answer<Object> store = delegatesTo(auditDao);
        LogActionTestHooks.useOscarLogDao(mock(OscarLogDao.class, call -> {
            if (call.getArguments().length == 1 && call.getArgument(0) instanceof OscarLog entry) {
                auditWrites.add(new AuditWrite(entry.getAction(), TransactionState.current()));
            }
            return store.answer(call);
        }));

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(42))).thenReturn(true);
        when(ticklerManager.getTicklerDemographicNoForUpdate(any(), eq(7))).thenReturn(42);
        when(ticklerManager.getTicklers(any(), any(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean())).thenAnswer(call -> {
                    resourceTransactions.add(TransactionState.current());
                    return List.of();
                });
    }

    @AfterEach
    void releaseAuditDatabase() throws Exception {
        LogActionTestHooks.useOscarLogDao(null);
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    /** Makes the resource write a row in its own transaction and then return or fail. */
    private void completeTicklerWritesARow(RuntimeException failure) {
        doAnswer(call -> {
            resourceTransactions.add(TransactionState.current());
            OscarLog row = new OscarLog();
            row.setAction(RESOURCE_WRITE);
            auditDao.persist(row);
            if (failure != null) {
                throw failure;
            }
            return null;
        }).when(ticklerManager).completeTickler(any(), eq(7), any());
    }

    private int getMyTicklers() {
        try (Response response = request().path("/tickler/mine").get()) {
            return response.getStatus();
        }
    }

    private int completeTickler() {
        try (Response response = request().path("/tickler/complete").post(Entity.json("{\"ticklers\":[7]}"))) {
            return response.getStatus();
        }
    }

    private int rows(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action = ?", Integer.class, action);
    }

    @Test
    @DisplayName("a read in a read-only transaction is audited after that transaction has completed")
    void shouldWriteAuditOutsideReadOnlyTransaction_whenTransactionalReadSucceeds() {
        assertThat(getMyTicklers()).isEqualTo(200);

        // Guards the fixture: the resource really ran in the read-only transaction it declares.
        assertThat(resourceTransactions).containsExactly(new TransactionState(true, true));
        assertThat(auditWrites).containsExactly(new AuditWrite(READ_AUDIT, NO_TRANSACTION));
        assertThat(rows(READ_AUDIT)).isEqualTo(1);
    }

    @Test
    @DisplayName("a failed write leaves its FAILURE audit row although its own transaction rolled back")
    void shouldKeepFailureAuditRow_whenResourceTransactionRollsBack() {
        completeTicklerWritesARow(new IllegalStateException("synthetic failure after a write"));

        assertThat(completeTickler()).isEqualTo(500);

        assertThat(resourceTransactions).containsExactly(new TransactionState(true, false));
        assertThat(rows(RESOURCE_WRITE)).as("the resource transaction rolled back").isZero();
        assertThat(auditWrites).containsExactly(new AuditWrite(WRITE_FAILURE_AUDIT, NO_TRANSACTION));
        assertThat(rows(WRITE_FAILURE_AUDIT)).isEqualTo(1);
    }

    @Test
    @DisplayName("a successful write is audited once its transaction has committed")
    void shouldWriteSuccessAuditAfterCommit_whenTransactionalWriteSucceeds() {
        completeTicklerWritesARow(null);

        assertThat(completeTickler()).isEqualTo(200);

        assertThat(rows(RESOURCE_WRITE)).as("the resource transaction committed").isEqualTo(1);
        assertThat(auditWrites).containsExactly(new AuditWrite(WRITE_AUDIT, NO_TRANSACTION));
        assertThat(rows(WRITE_AUDIT)).isEqualTo(1);
        assertThat(rows(WRITE_FAILURE_AUDIT)).isZero();
    }

    @Test
    @DisplayName("a failing audit insert does not change the answer of a successful read")
    void shouldKeepSuccessfulResponse_whenAuditInsertFails() {
        auditStoreFailure = new PersistenceException("synthetic audit store failure");

        assertThat(getMyTicklers()).isEqualTo(200);

        assertThat(auditWrites).extracting(AuditWrite::action).containsExactly(READ_AUDIT);
        assertThat(rows(READ_AUDIT)).isZero();
    }

    @Test
    @DisplayName("a failing audit insert does not undo a successful write")
    void shouldKeepCommittedWrite_whenAuditInsertFails() {
        completeTicklerWritesARow(null);
        auditStoreFailure = new PersistenceException("synthetic audit store failure");

        assertThat(completeTickler()).isEqualTo(200);

        assertThat(rows(RESOURCE_WRITE)).isEqualTo(1);
        assertThat(rows(WRITE_AUDIT)).isZero();
    }

    /**
     * The audit insert opens its own transaction, so the rows survive even where the advice order
     * is lost and the audit advice runs inside the resource transaction again.
     */
    @Nested
    @DisplayName("with the transaction advisor ordered outside the audit advice")
    class WithTransactionAdvisorOutermost {

        WithTransactionAdvisorOutermost() {
            // Runs before the outer @BeforeEach methods build the context.
            transactionAdvisorOrder = String.valueOf(Ordered.HIGHEST_PRECEDENCE);
        }

        @Test
        @DisplayName("a read in a read-only transaction still succeeds and leaves its audit row")
        void shouldCommitAuditIndependently_whenWrittenInsideReadOnlyTransaction() {
            assertThat(getMyTicklers()).isEqualTo(200);

            // Guards the fixture: here the audit advice does run inside the resource transaction.
            assertThat(auditWrites).containsExactly(new AuditWrite(READ_AUDIT, new TransactionState(true, true)));
            assertThat(rows(READ_AUDIT)).isEqualTo(1);
        }

        @Test
        @DisplayName("a failed write still leaves its FAILURE audit row")
        void shouldKeepFailureAuditRow_whenWrittenInsideRolledBackTransaction() {
            completeTicklerWritesARow(new IllegalStateException("synthetic failure after a write"));

            assertThat(completeTickler()).isEqualTo(500);

            assertThat(rows(RESOURCE_WRITE)).as("the resource transaction rolled back").isZero();
            assertThat(rows(WRITE_FAILURE_AUDIT)).isEqualTo(1);
        }

        @Test
        @DisplayName("a failing audit insert does not roll the resource transaction back")
        void shouldKeepCommittedWrite_whenAuditInsertFailsInsideResourceTransaction() {
            completeTicklerWritesARow(null);
            auditStoreFailure = new PersistenceException("synthetic audit store failure");

            assertThat(completeTickler()).isEqualTo(200);

            assertThat(rows(RESOURCE_WRITE)).isEqualTo(1);
            assertThat(rows(WRITE_AUDIT)).isZero();
        }
    }
}
