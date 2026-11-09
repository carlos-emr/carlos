/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real JPA + JDBC COMMIT with a lost acknowledgement, including durable receipt/audit recovery. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxSaveReceiptCommitIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native") @Tag("prescription")
class RxSaveReceiptCommitIntegrationTest extends CarlosUnitTestBase {
    @Autowired private DataSource source;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private RxSubmissionReceiptService receipts;
    @Autowired private DrugDao drugs;
    @Autowired private PartialDateDao partialDates;
    @Autowired private OscarLogDao audit;
    @Autowired private CommitFault fault;
    @PersistenceContext private EntityManager em;

    @BeforeEach void prepare() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of(
                "database/mysql/migration/common/V1.0.59.36__rx_submission_receipts.sql"));
        try (var connection = source.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().contains("H2")) {
                sql = sql.replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci", "");
            }
        }
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(source);
        new JdbcTemplate(source).update("DELETE FROM rx_submission_receipt");
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            em.createQuery("delete from OscarLog").executeUpdate();
            em.createQuery("delete from PartialDate").executeUpdate();
            em.createQuery("delete from Drug").executeUpdate();
            em.createQuery("delete from Prescription").executeUpdate();
        });
        logActionMock.close(); logActionMock = null;
        setAudit(audit);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(RxManager.class, mock(RxManager.class));
        registerMock(DrugDao.class, drugs);
        registerMock(PartialDateDao.class, partialDates);
        registerMock(PlatformTransactionManager.class, transactions);
        registerMock(RxSubmissionReceiptService.class, receipts);
    }
    @AfterEach void resetFault() { fault.remaining.set(0); fault.commitBeforeThrow = true; setAudit(null); }

    private static void setAudit(OscarLogDao dao) {
        try {
            var setter = LogAction.class.getDeclaredMethod("setOscarLogDaoForTesting", OscarLogDao.class);
            setter.setAccessible(true); setter.invoke(null, dao);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"lost-ack", "audit-failure", "failed-commit", "intent-rollback", "intent-lost-ack", "begin-failure", "rollback-only"})
    void shouldRecoverReceiptAndAudit_whenAcknowledgementOrAuditFails(String scenario) throws Exception {
        boolean intentFailure = scenario.startsWith("intent-");
        boolean beginFailure = "begin-failure".equals(scenario);
        boolean auditFailure = "audit-failure".equals(scenario);
        boolean rollbackOnly = "rollback-only".equals(scenario);
        boolean failedCommit = "failed-commit".equals(scenario);
        var bean = new RxSessionBean(); bean.setProviderNo("999998"); bean.setDemographicNo(1001);
        var card = new RxPrescriptionData.Prescription(0, "999998", 1001);
        card.setRandomId(123); card.setGCN_SEQNO("0"); card.setBrandName("Synthetic receipt medication");
        card.setSpecial("1 tab PO OD"); card.setRxDate(java.sql.Date.valueOf("2026-01-01"));
        bean.getStashList().add(card);
        String originalRevision = card.getDraftRevision();
        String token = RxSubmissionReceiptService.fingerprint(java.util.Map.of("123", originalRevision));
        var request = new MockHttpServletRequest("POST", "/rx/WriteScript");
        request.addParameter("demographicNo", "1001"); request.addParameter("draftRevision_123", originalRevision);
        var response = new MockHttpServletResponse();
        var info = mock(LoggedInInfo.class); when(info.getLoggedInProviderNo()).thenReturn("999998");
        var security = mock(SecurityInfoManager.class); registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(info), eq("_rx"), anyString(), eq(1001))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 1001)).thenReturn(true);
        try (var servlet = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             var headers = mockConstruction(RxPrescriptionData.class, (data, context) ->
                     when(data.saveScript(any(), any())).thenAnswer(invocation -> {
                         var header = new Prescription(); header.setProviderNo("999998"); header.setDemographicId(1001);
                         em.persist(header); return header.getId().toString();
                     }))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            if (beginFailure) {
                var beginFault = mock(PlatformTransactionManager.class);
                when(beginFault.getTransaction(any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Synthetic begin failure"));
                registerMock(PlatformTransactionManager.class, beginFault);
            } else if (auditFailure || rollbackOnly) {
                var failingAudit = mock(OscarLogDao.class, org.mockito.AdditionalAnswers.delegatesTo(audit));
                doAnswer(invocation -> {
                    OscarLog row = invocation.getArgument(0);
                    if ("add".equals(row.getAction())) {
                        if (auditFailure) throw new IllegalStateException("Synthetic authoritative audit failure");
                        new TransactionTemplate(transactions).executeWithoutResult(status -> status.setRollbackOnly());
                    }
                    audit.persist(row); return null;
                }).when(failingAudit).persist(any(OscarLog.class));
                setAudit(failingAudit);
            } else {
                fault.commitBeforeThrow = !failedCommit && !"intent-rollback".equals(scenario);
                fault.remaining.set(intentFailure ? 1 : 2); // Intent commits normally; clinical COMMIT then loses its acknowledgement.
            }
            assertThatThrownBy(() -> action.persistStash(info, bean)).isInstanceOf(RxWriteScript2Action.PrescriptionSaveException.class);
            if (intentFailure || beginFailure) {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isNull();
                assertThat(card.getPendingSubmission()).isNull();
                assertThat(card.getDraftRevision()).isEqualTo(originalRevision);
                assertThat(receipts.find("999998", 1001, token)).isNull();
                assertThat(new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                registerMock(PlatformTransactionManager.class, transactions);
                action.persistStash(info, bean);
                assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("SAVED");
                return;
            }
            if (auditFailure || rollbackOnly) {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isNull();
                assertThat(card.getDraftRevision()).isEqualTo(originalRevision);
                assertThat(receipts.find("999998", 1001, token)).isNull();
                var failedJdbc = new JdbcTemplate(source);
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add'", Long.class)).isZero();
                setAudit(audit);
                action.persistStash(info, bean); // Only a confirmed rollback releases the exact draft for retry.
            } else {
                assertThat(request.getAttribute("RX_SAVE_OUTCOME_UNKNOWN")).isEqualTo(true);
            }
            if (failedCommit) {
                assertThat(card.getPendingSubmission()).isEqualTo(token);
                bean.removePersistedStashItems();
                assertThat(bean.getStashSize()).isEqualTo(1); // Reopening must retain the editable, locked draft.
                assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
                request.setParameter("draftRevision_123", card.getDraftRevision()); // New rendered revision after reopening.
                try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                    resolver.when(() -> RxSessionBeanResolver.requestedDemographicNo(request)).thenReturn(1001);
                    resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(bean);
                    resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, bean)).thenReturn(true);
                    assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
                }
                assertThat(response.getStatus()).isEqualTo(409);
                assertThat(response.getContentAsString()).contains("RX_SAVE_OUTCOME_UNKNOWN");
                var failedJdbc = new JdbcTemplate(source);
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isZero();
                assertThat(failedJdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add'", Long.class)).isZero();
                return;
            }
            assertThat(card.getDraftRevision()).isNotEqualTo(originalRevision);
            var receipt = receipts.find("999998", 1001, token);
            assertThat(receipt.outcome()).isEqualTo("SAVED");
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM log WHERE action='add' AND content='prescription'", Long.class)).isEqualTo(1L);
            assertThat(receipts.find("999997", 1001, token)).isNull();
            if (!auditFailure && !rollbackOnly) {
                final var replayResponse = new org.springframework.mock.web.MockHttpServletResponse();
                servlet.when(ServletActionContext::getResponse).thenReturn(replayResponse);
                // Action retains its response field, so construct a new request-scoped action.
                var replayAction = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
                request.setParameter("draftRevision_123", card.getDraftRevision());
                var newer = new RxPrescriptionData.Prescription(0, "999998", 1001);
                newer.setRandomId(456); newer.setSpecial("Unsaved newer draft");
                bean.getStashList().add(newer);
                request.setParameter("draftRevision_456", newer.getDraftRevision());
                request.setParameter("drugName_123", "Earlier saved medication");
                request.setParameter("drugName_456", "Newer unsaved medication");
                try (var resolver = mockStatic(RxSessionBeanResolver.class)) {
                    resolver.when(() -> RxSessionBeanResolver.requestedDemographicNo(request)).thenReturn(1001);
                    resolver.when(() -> RxSessionBeanResolver.resolve(request)).thenReturn(bean);
                    resolver.when(() -> RxSessionBeanResolver.isRequestForBeanPatient(request, bean)).thenReturn(true);
                    assertThat(replayAction.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
                    assertThat(bean.getStash()).containsExactly(newer);
                    assertThat(newer.getDrugId()).isZero();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prescription", Long.class)).isEqualTo(1L);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
                    assertThat(replayResponse.getContentAsString()).contains("RX_DRAFT_REVIEW_REQUIRED");
                }
                request.removeParameter("draftRevision_456");
                request.removeParameter("drugName_456");
                request.setParameter("draftRevision_123", originalRevision);
            }
            // No Rx bean in this new request/session: replay is resolved from durable, ownership-scoped evidence.
            assertThat(action.updateSaveAllDrugs()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getContentAsString()).contains("RX_ALREADY_SAVED");
            var legacyResponse = new MockHttpServletResponse();
            servlet.when(ServletActionContext::getResponse).thenReturn(legacyResponse);
            var legacy = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            legacy.setAction("updateAndPrint");
            assertThat(legacy.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
            assertThat(legacyResponse.getStatus()).isEqualTo(409);
            assertThat(legacyResponse.getContentType()).startsWith("text/html");
            assertThat(legacyResponse.getContentAsString()).contains("already saved").doesNotContain("RX_ALREADY_SAVED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drugs", Long.class)).isEqualTo(1L);
        }
    }

    @Test void shouldKeepPendingIntentAcrossNewManager_whenNoClinicalCommitWasConfirmed() {
        String token = RxSubmissionReceiptService.fingerprint(java.util.Map.of("1", java.util.UUID.randomUUID().toString()));
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isTrue();
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isFalse();
        var reopened = new RxSubmissionReceiptService(source);
        assertThat(reopened.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
        receipts.failed("999998", 1001, token, "00000000-0000-0000-0000-000000000002");
        assertThat(receipts.find("999998", 1001, token).outcome()).isEqualTo("PENDING");
        receipts.failed("999998", 1001, token, "00000000-0000-0000-0000-000000000001");
        assertThat(receipts.find("999998", 1001, token)).isNull();
        assertThat(receipts.start("999998", 1001, token, "00000000-0000-0000-0000-000000000001", "127.0.0.1")).isTrue();
    }

    static class CommitFault {
        final AtomicInteger remaining = new AtomicInteger();
        volatile boolean commitBeforeThrow = true;
        Connection wrap(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if (method.getName().equals("commit") && remaining.get() > 0 && remaining.decrementAndGet() == 0) {
                                if (commitBeforeThrow) delegate.commit();
                                else delegate.rollback(); // Simulate disconnect before COMMIT reached the server.
                                throw new SQLException("Synthetic lost COMMIT acknowledgement", "08006");
                            }
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        }
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase database() throws Exception { return new IsolatedJpaTestDatabase("rx_commit_receipt"); }
        @Bean CommitFault fault() { return new CommitFault(); }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase db, CommitFault fault) {
            return new DelegatingDataSource(db.dataSource()) {
                @Override public Connection getConnection() throws SQLException { return fault.wrap(super.getConnection()); }
                @Override public Connection getConnection(String user, String password) throws SQLException { return fault.wrap(super.getConnection(user, password)); }
            };
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, IsolatedJpaTestDatabase db) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(Drug.class.getName(), Prescription.class.getName(), PartialDate.class.getName(), OscarLog.class.getName(), Favorites.class.getName(), FavoritesPrivilege.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); factory.setJpaPropertyMap(db.jpaProperties()); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean DrugDao drugs() { return new DrugDaoImpl(); }
        @Bean PartialDateDao dates() { return new PartialDateDaoImpl(); }
        @Bean OscarLogDao audit() { return new OscarLogDaoImpl(); }
        @Bean FavoritesDao favorites() { return new FavoritesDaoImpl(); }
        @Bean FavoritesPrivilegeDao favoritePrivileges() { return new FavoritesPrivilegeDaoImpl(); }
        @Bean RxFavoriteCopyIntegrationTest.CopyFault copyFault() { return new RxFavoriteCopyIntegrationTest.CopyFault(); }
        @Bean RxFavoriteCopyService favoriteCopies(FavoritesDao favorites, FavoritesPrivilegeDao privileges,
                RxFavoriteCopyIntegrationTest.CopyFault fault) {
            var observed = mock(FavoritesDao.class, org.mockito.AdditionalAnswers.delegatesTo(favorites));
            doAnswer(invocation -> {
                if (fault.remaining.get() > 0 && fault.remaining.decrementAndGet() == 0) {
                    throw new IllegalStateException("Synthetic second favourite insert failure");
                }
                favorites.persist(invocation.getArgument(0)); return null;
            }).when(observed).persist(any(Favorites.class));
            return new RxFavoriteCopyService(observed, privileges);
        }
        @Bean RxSourceVersionValidator concurrency(DataSource source) { return new RxSourceVersionValidator(source); }
        @Bean RxSubmissionReceiptService receipts(DataSource source) { return new RxSubmissionReceiptService(source); }
    }
}
