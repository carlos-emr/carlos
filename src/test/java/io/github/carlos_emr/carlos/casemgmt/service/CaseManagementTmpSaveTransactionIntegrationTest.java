/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDaoImpl;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real temporary-note DAO and service through Spring JPA transactions.
 * The isolated H2 database is unique to this class and destroyed when its factory closes.
 * No test transaction surrounds a service call, so replacement must commit or roll back itself.
 *
 * @since 2026-10-24
 */
@Tag("integration")
@Tag("casemgmt")
class CaseManagementTmpSaveTransactionIntegrationTest {
    private static EntityManagerFactory factory;
    private static JpaTransactionManager transactions;
    private static CaseManagementManager manager;

    @BeforeAll
    static void openDatabase() {
        factory = new Configuration().addAnnotatedClass(CaseManagementTmpSave.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:temporary_notes_" + UUID.randomUUID()
                        + ";MODE=MySQL;LOCK_TIMEOUT=10000")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        transactions = new JpaTransactionManager(factory);

        CaseManagementTmpSaveDaoImpl dao = new CaseManagementTmpSaveDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", SharedEntityManagerCreator.createSharedEntityManager(factory));
        ReflectionTestUtils.setField(dao, "entityManagerFactory", factory);
        ProxyFactory daoProxy = new ProxyFactory(dao);
        daoProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));

        CaseManagementManagerImpl service = new CaseManagementManagerImpl();
        service.setCaseManagementTmpSaveDao((CaseManagementTmpSaveDao) daoProxy.getProxy());
        ProxyFactory serviceProxy = new ProxyFactory(service);
        serviceProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        manager = (CaseManagementManager) serviceProxy.getProxy();
    }

    @AfterAll
    static void closeDatabase() {
        if (factory != null) factory.close();
    }

    @BeforeEach
    void seedIndependentDrafts() {
        write(em -> {
            em.createNativeQuery("ALTER TABLE casemgmt_tmpsave DROP CONSTRAINT IF EXISTS temporary_note_failure")
                    .executeUpdate();
            em.createQuery("DELETE FROM CaseManagementTmpSave").executeUpdate();
            seed(em, "999998", 101, 11, 501, "Original target draft");
            seed(em, "999998", 101, 11, 502, "Earlier target draft");
            seed(em, "999998", 101, 22, 503, "Same patient, another program");
            seed(em, "999998", 202, 11, 504, "Another patient, same program");
            seed(em, "999997", 101, 11, 505, "Another provider, same patient and program");
            return null;
        });
    }

    @Test
    void shouldCommitOnlyTheTargetReplacement_whenOtherDraftContextsExist() {
        List<Draft> unrelatedBefore = unrelated(rows());

        manager.replaceTmpSave("999998", "101", "11", "601", "Replacement <literal> & draft");

        List<Draft> after = rows();
        assertThat(after).hasSize(4);
        assertThat(unrelated(after)).containsExactlyElementsOf(unrelatedBefore);
        assertThat(after).filteredOn(row -> row.provider().equals("999998") && row.patient() == 101
                && row.program() == 11).singleElement().satisfies(row -> {
                    assertThat(row.noteId()).isEqualTo(601);
                    assertThat(row.note()).isEqualTo("Replacement <literal> & draft");
                    assertThat(row.updated()).isPositive();
                });
    }

    @Test
    void shouldRestoreDeletedDrafts_whenReplacementParsingFails() {
        List<Draft> before = rows();

        assertThatThrownBy(() -> manager.replaceTmpSave("999998", "101", "11", "invalid-note-id", "Rejected draft"))
                .isInstanceOf(NumberFormatException.class);

        assertThat(rows()).containsExactlyElementsOf(before);
        manager.replaceTmpSave("999998", "101", "11", "602", "Successful retry");
        assertThat(unrelated(rows())).containsExactlyElementsOf(unrelated(before));
        assertThat(rows()).filteredOn(row -> row.provider().equals("999998") && row.patient() == 101
                && row.program() == 11).singleElement().satisfies(row -> {
                    assertThat(row.noteId()).isEqualTo(602);
                    assertThat(row.note()).isEqualTo("Successful retry");
                });
    }

    @Test
    void shouldRestoreDeletedDraftsAndPermitRetry_whenTheDatabaseRejectsTheReplacement() {
        write(em -> em.createNativeQuery("ALTER TABLE casemgmt_tmpsave ADD CONSTRAINT temporary_note_failure "
                + "CHECK (note <> 'Storage rejected draft')").executeUpdate());
        List<Draft> before = rows();

        assertThatThrownBy(() -> manager.replaceTmpSave("999998", "101", "11", "603", "Storage rejected draft"))
                .isInstanceOf(RuntimeException.class);

        assertThat(rows()).containsExactlyElementsOf(before);
        manager.replaceTmpSave("999998", "101", "11", "603", "Storage accepted retry");
        assertThat(unrelated(rows())).containsExactlyElementsOf(unrelated(before));
        assertThat(rows()).filteredOn(row -> row.provider().equals("999998") && row.patient() == 101
                && row.program() == 11).singleElement().satisfies(row -> {
                    assertThat(row.noteId()).isEqualTo(603);
                    assertThat(row.note()).isEqualTo("Storage accepted retry");
                });
    }

    private static void seed(EntityManager em, String provider, int patient, int program, int noteId, String text) {
        CaseManagementTmpSave draft = new CaseManagementTmpSave();
        draft.setProviderNo(provider);
        draft.setDemographicNo(patient);
        draft.setProgramId(program);
        draft.setNoteId(noteId);
        draft.setNote(text);
        draft.setUpdateDate(new Date(1_700_000_000_000L));
        em.persist(draft);
    }

    private static <T> T write(Function<EntityManager, T> body) {
        return new TransactionTemplate(transactions).execute(status ->
                body.apply(EntityManagerFactoryUtils.getTransactionalEntityManager(factory)));
    }

    private static List<Draft> rows() {
        return write(em -> em.createQuery("SELECT d FROM CaseManagementTmpSave d ORDER BY d.id", CaseManagementTmpSave.class)
                .getResultList().stream().map(draft -> new Draft(draft.getId(), draft.getProviderNo(),
                        draft.getDemographicNo(), draft.getProgramId(), draft.getNoteId(), draft.getNote(),
                        draft.getUpdateDate().getTime())).toList());
    }

    private static List<Draft> unrelated(List<Draft> drafts) {
        return drafts.stream().filter(row -> !row.provider().equals("999998") || row.patient() != 101
                || row.program() != 11).toList();
    }

    private record Draft(int id, String provider, int patient, int program, int noteId, String note, long updated) { }
}
