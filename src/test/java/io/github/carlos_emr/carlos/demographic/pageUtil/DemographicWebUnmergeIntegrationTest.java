/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The actual web action/helper/DAO transactions, observed over another JDBC connection (#6004). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DemographicWebUnmergeIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
@Tag("mariadb-native")
class DemographicWebUnmergeIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private DemographicDao patients;
    @Autowired private DemographicMergedDao merges;
    @Autowired private SecObjPrivilegeDao privileges;
    @Autowired private RecycleBinDao recycle;
    @Autowired private OscarLogDao audits;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;
    private JdbcTemplate sql;
    private LoggedInInfo actor;
    private SecurityInfoManager security;
    private RecycleBinDao selectedRecycle;
    private Object previousAuditDao;
    private int first;
    private int second;
    private int untouched;

    @BeforeEach
    void prepareOwnedRecords() throws Exception {
        sql = new JdbcTemplate(dataSource);
        if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
            sql.execute("CREATE TABLE IF NOT EXISTS admission(client_id INT, admission_status VARCHAR(20), program_id INT)");
            sql.execute("CREATE TABLE IF NOT EXISTS program(id INT, type VARCHAR(20))");
            sql.execute("CREATE TABLE IF NOT EXISTS health_safety(demographic_no INT)");
            sql.execute("CREATE TABLE IF NOT EXISTS lst_gender(code VARCHAR(10), description VARCHAR(100))");
        }
        var provider = new Provider();
        provider.setProviderNo("999998");
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProvider()).thenReturn(provider);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        when(actor.getIp()).thenReturn("127.0.0.1");
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(actor, "_demographic", "w", null)).thenReturn(true);
        selectedRecycle = recycle;
        previousAuditDao = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", audits);
        var ids = new TransactionTemplate(transactionManager).execute(status -> {
            var people = new java.util.ArrayList<Integer>();
            for (int i = 0; i < 4; i++) {
                var person = new Demographic();
                person.setFirstName("Synthetic"); person.setLastName("WebUnmerge6004");
                person.setPrefName(""); person.setSex("M");
                em.persist(person);
                people.add(person.getDemographicNo());
            }
            for (int child : people.subList(1, 4)) {
                var link = new DemographicMerged();
                link.setDemographicNo(child); link.setMergedTo(people.getFirst());
                link.setDeleted(0); link.setLastUpdateUser("999998"); link.setLastUpdateDate(new Date());
                em.persist(link);
                var restriction = new SecObjPrivilege();
                restriction.setId(new SecObjPrivilegePrimaryKey("_all", "_eChart$" + child));
                restriction.setPrivilege("|or|"); restriction.setPriority(0); restriction.setProviderNo("0");
                em.persist(restriction);
            }
            return people;
        });
        first = ids.get(1); second = ids.get(2); untouched = ids.get(3);
    }

    @AfterEach
    void restoreAuditDependency() {
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousAuditDao);
    }

    private String unmerge(String requestProvider, String... ids) {
        var request = new MockHttpServletRequest();
        request.setMethod("POST"); request.setParameter("mergeAction", "unmerge");
        request.setParameter("provider_no", requestProvider); request.setParameter("records", ids);
        try (var beans = mockStatic(SpringUtils.class);
             var servlet = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             MockedStatic<LogAction> log = mockStatic(LogAction.class, CALLS_REAL_METHODS)) {
            beans.when(() -> SpringUtils.getBean(DemographicMergedDao.class)).thenReturn(merges);
            beans.when(() -> SpringUtils.getBean(SecObjPrivilegeDao.class)).thenReturn(privileges);
            beans.when(() -> SpringUtils.getBean(RecycleBinDao.class)).thenReturn(selectedRecycle);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(actor);
            // Keep the legacy best-effort executor out of this isolated database. Strict audit
            // calls remain real and must join the transaction that owns the data change.
            log.when(() -> LogAction.addLog(any(LoggedInInfo.class), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenAnswer(invocation -> null);
            return new DemographicMergeRecord2Action(security, patients, transactionManager).execute();
        }
    }

    private long active(int patient) {
        return sql.queryForObject("SELECT COUNT(*) FROM demographic_merged WHERE demographic_no=? AND deleted=0", Long.class, patient);
    }

    private long restrictions(int patient) {
        return sql.queryForObject("SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup='_all' AND objectName=?", Long.class, "_eChart$" + patient);
    }

    private long recycled(int patient) {
        return sql.queryForObject("SELECT COUNT(*) FROM recyclebin WHERE keyword=?", Long.class, "_all|_eChart$" + patient);
    }

    private long logged(int patient) {
        return sql.queryForObject("SELECT COUNT(*) FROM log WHERE action='DemographicMerged.UnMerge' AND demographic_no=?", Long.class, patient);
    }

    private void assertStillMerged(int patient) {
        assertThat(active(patient)).isEqualTo(1);
        assertThat(restrictions(patient)).isEqualTo(1);
        assertThat(recycled(patient)).isZero();
        assertThat(logged(patient)).isZero();
    }

    @Test
    void shouldUseAuthenticatedAttribution_whenTheRequestSuppliesAnotherProvider() {
        assertThat(unmerge("777777", Integer.toString(first))).isEqualTo("successUnMerge");
        assertThat(sql.queryForObject("SELECT provider_no FROM recyclebin WHERE keyword=?", String.class,
                "_all|_eChart$" + first)).isEqualTo("999998");
        assertThat(logged(first)).isEqualTo(1);
        assertStillMerged(untouched);
    }

    @Test
    void shouldRollbackEveryParticipant_whenTheLaterRecycleWriteFails() {
        selectedRecycle = mock(RecycleBinDao.class);
        var writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new DataIntegrityViolationException("Synthetic second recycle failure");
            recycle.persist(invocation.getArgument(0));
            return null;
        }).when(selectedRecycle).persist(any(RecycleBin.class));
        assertThatThrownBy(() -> unmerge("999998", Integer.toString(first), Integer.toString(second)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessage("Synthetic second recycle failure");
        assertThat(writes.get()).isEqualTo(2);
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Test
    void shouldRollbackEveryParticipant_whenALaterStorageValueIsMalformed() {
        selectedRecycle = mock(RecycleBinDao.class);
        var writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new NumberFormatException("Synthetic malformed stored value");
            recycle.persist(invocation.getArgument(0));
            return null;
        }).when(selectedRecycle).persist(any(RecycleBin.class));
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() ->
                unmerge("999998", Integer.toString(first), Integer.toString(second)));
        assertThat(writes.get()).isEqualTo(2);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThat(failure).isInstanceOf(NumberFormatException.class),
                () -> assertStillMerged(first), () -> assertStillMerged(second), () -> assertStillMerged(untouched));
    }

    @Test
    void shouldRollbackEveryParticipant_whenTheLaterAuditWriteFails() {
        var failing = mock(OscarLogDao.class);
        var writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new DataIntegrityViolationException("Synthetic second audit failure");
            audits.persist(invocation.getArgument(0));
            return null;
        }).when(failing).persist(any(OscarLog.class));
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", failing);
        assertThatThrownBy(() -> unmerge("999998", Integer.toString(first), Integer.toString(second)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessage("Synthetic second audit failure");
        assertThat(writes.get()).isEqualTo(2);
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Test
    void shouldCommitOnlySelectedParticipants_whenTheBatchSucceeds() {
        assertThat(unmerge("999998", Integer.toString(first), Integer.toString(second))).isEqualTo("successUnMerge");
        assertThat(active(first)).isZero(); assertThat(active(second)).isZero();
        assertThat(restrictions(first)).isZero(); assertThat(restrictions(second)).isZero();
        assertThat(recycled(first)).isEqualTo(1); assertThat(recycled(second)).isEqualTo(1);
        assertThat(logged(first)).isEqualTo(1); assertThat(logged(second)).isEqualTo(1);
        assertStillMerged(untouched);
    }

    @Test
    void shouldCanonicalizeAndDeduplicate_whenSelectedIdsHaveEquivalentSpellings() {
        assertThat(unmerge("777777", "0" + first, "+" + first, Integer.toString(first))).isEqualTo("successUnMerge");
        assertThat(active(first)).isZero(); assertThat(restrictions(first)).isZero();
        assertThat(recycled(first)).isEqualTo(1); assertThat(logged(first)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT provider_no FROM recyclebin WHERE keyword=?", String.class,
                "_all|_eChart$" + first)).isEqualTo("999998");
        assertStillMerged(second); assertStillMerged(untouched);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "not-a-number", "2147483648", " ", ""})
    void shouldRefuseTheWholeSelection_whenAnyIdIsInvalid(String invalid) {
        assertThat(unmerge("999998", Integer.toString(first), invalid)).isEqualTo("failure");
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Test
    void shouldRefuseTheWholeSelection_whenAnyPatientIsMissing() {
        assertThat(unmerge("999998", Integer.toString(first), Integer.toString(Integer.MAX_VALUE))).isEqualTo("failure");
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Test
    void shouldKeepTheFailureOutcome_whenTheSelectionIsEmpty() {
        assertThat(unmerge("999998")).isEqualTo("failure");
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Test
    void shouldRefuseBeforeWrites_whenTheCallerLacksWritePrivilege() {
        when(security.hasPrivilege(actor, "_demographic", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> unmerge("999998", Integer.toString(first))).isInstanceOf(SecurityException.class);
        assertStillMerged(first); assertStillMerged(second); assertStillMerged(untouched);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("web_unmerge_6004");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase database) { return database.dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, IsolatedJpaTestDatabase database) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(Demographic.class.getName(), DemographicMerged.class.getName(),
                    Provider.class.getName(), OscarLog.class.getName(), SecObjPrivilege.class.getName(), RecycleBin.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(database.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean DemographicDao demographicDao() { return new DemographicDaoImpl(); }
        @Bean DemographicMergedDao demographicMergedDao() { return new DemographicMergedDaoImpl(); }
        @Bean SecObjPrivilegeDao privilegeDao() { return new SecObjPrivilegeDaoImpl(); }
        @Bean RecycleBinDao recycleDao() { return new RecycleBinDaoImpl(); }
        @Bean OscarLogDao auditDao() { return new OscarLogDaoImpl(); }
    }
}
