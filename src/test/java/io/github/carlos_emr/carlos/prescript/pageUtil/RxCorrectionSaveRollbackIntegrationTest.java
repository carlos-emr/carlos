/*
 * Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Uses real prescription, drug and partial-date writes to verify late save failures roll back together. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxCorrectionSaveRollbackIntegrationTest.Config.class)
@Tag("integration")
@Tag("prescription")
@Tag("mariadb-native")
class RxCorrectionSaveRollbackIntegrationTest extends CarlosUnitTestBase {
    @Autowired private DrugDao drugDao;
    @Autowired private PartialDateDao partialDates;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    @Autowired private DataSource dataSource;
    private boolean insertFaultInstalled;

    private void installNativeInsertFault() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            if (!connection.getMetaData().getDatabaseProductVersion().contains("MariaDB")) return;
            // The packaged non-strict SQL mode can truncate a long BN instead of
            // rejecting it as H2 does. Force the same late INSERT failure on the
            // physical server, scoped to this class's independently owned schema.
            insertFaultInstalled = true;
            statement.execute("CREATE TRIGGER carlos_rx_second_insert_fault BEFORE INSERT ON drugs FOR EACH ROW "
                    + "BEGIN IF NEW.demographic_no=1001 AND LEFT(NEW.BN,32)=REPEAT('X',32) THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic second insert failure'; END IF; END");
        }
    }

    @AfterEach
    void removeNativeInsertFault() throws Exception {
        if (insertFaultInstalled) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS carlos_rx_second_insert_fault");
            }
            insertFaultInstalled = false;
        }
    }

    @Test
    void shouldRollbackPartialDates_whenSecondDrugInsertFails() throws Exception {
        installNativeInsertFault();
        var template = new TransactionTemplate(transactions);
        var first = new RxPrescriptionData.Prescription(0, "999998", 1001);
        first.setRandomId(1);
        first.setGCN_SEQNO("0");
        first.setBrandName("Synthetic first drug");
        first.setSpecial("1 tab PO OD");
        first.setRxDate(java.sql.Date.valueOf("2026-01-01"));
        first.setWrittenDate(java.sql.Date.valueOf("2026-01-01"));
        first.setWrittenDateFormat(PartialDate.YEARONLY);
        var second = new RxPrescriptionData.Prescription(0, "999998", 1001);
        second.setRandomId(2);
        second.setGCN_SEQNO("0");
        second.setBrandName("X".repeat(256)); // H2's real drugs.BN VARCHAR(255) rejects this INSERT.
        second.setSpecial("2 tabs PO OD");
        second.setRxDate(java.sql.Date.valueOf("2026-01-01"));
        var bean = new RxSessionBean();
        bean.setDemographicNo(1001);
        bean.setProviderNo("999998");
        bean.getStashList().addAll(List.of(first, second));
        var observedPartialWrite = new AtomicBoolean();
        DrugDao observedDrugs = mock(DrugDao.class, delegatesTo(drugDao));
        doAnswer(invocation -> {
            Drug row = invocation.getArgument(0);
            if (row.getBrandName().length() > 255) {
                assertThat(count("Drug")).isEqualTo(1);
                assertThat(count("PartialDate")).isEqualTo(1);
                assertThat(drugDao.find(first.getDrugId()).getWrittenDate()).isEqualTo(first.getWrittenDate());
                assertThat(partialDates.getPartialDate(PartialDate.DRUGS, first.getDrugId(), PartialDate.DRUGS_WRITTENDATE)
                        .getFormat()).isEqualTo(PartialDate.YEARONLY);
                observedPartialWrite.set(true);
            }
            drugDao.persist(row);
            return null;
        }).when(observedDrugs).persist(any(Drug.class));
        var receipts = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.RxSubmissionReceiptService.class);
        org.mockito.Mockito.when(receipts.start(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        registerMock(io.github.carlos_emr.carlos.managers.RxSubmissionReceiptService.class, receipts);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(PartialDateDao.class, partialDates);
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(RxManager.class, mock(RxManager.class));
        registerMock(DrugDao.class, observedDrugs);
        registerMock(PlatformTransactionManager.class, transactions);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        String firstRevision = first.getDraftRevision();
        String secondRevision = second.getDraftRevision();
        try (var servlet = mockStatic(ServletActionContext.class);
             var _ = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                     when(mock.saveScript(any(), any())).thenAnswer(invocation -> {
                         var header = new Prescription();
                         header.setProviderNo("999998");
                         header.setDemographicId(1001);
                         entityManager.persist(header);
                         return header.getId().toString();
                     }))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));

            var failure = assertThatThrownBy(() -> action.persistStash(info, bean))
                    .isInstanceOf(RxWriteScript2Action.PrescriptionSaveException.class)
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
            if (insertFaultInstalled) failure.rootCause().hasMessageContaining("Synthetic second insert failure");
            else failure.rootCause().hasMessageContaining("Value too long for column");

            assertThat(observedPartialWrite).isTrue();
            template.executeWithoutResult(status -> {
                assertThat(count("Drug")).isZero();
                assertThat(count("Prescription")).isZero();
                assertThat(count("PartialDate")).isZero();
            });
            assertThat(first.getDrugId()).isZero();
            assertThat(second.getDrugId()).isZero();
            assertThat(first.getDraftRevision()).isEqualTo(firstRevision);
            assertThat(second.getDraftRevision()).isEqualTo(secondRevision);
            logActionMock.verifyNoInteractions();

            second.setBrandName("Synthetic second drug");
            assertThat(action.persistStash(info, bean)).isNotBlank();
            template.executeWithoutResult(status -> {
                assertThat(count("Drug")).isEqualTo(2);
                assertThat(count("Prescription")).isEqualTo(1);
                assertThat(count("PartialDate")).isEqualTo(1);
                assertThat(partialDates.getPartialDate(PartialDate.DRUGS, first.getDrugId(), PartialDate.DRUGS_WRITTENDATE)
                        .getFormat()).isEqualTo(PartialDate.YEARONLY);
            });
        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createQuery("delete from PartialDate").executeUpdate();
                entityManager.createQuery("delete from Drug").executeUpdate();
                entityManager.createQuery("delete from Prescription").executeUpdate();
            });
        }
    }

    @Test
    void shouldRollbackRowsAndWorkspaceAndRetryOriginals_whenSecondArchiveFails() {
        var template = new TransactionTemplate(transactions);
        Drug first = original();
        Drug second = original();
        template.executeWithoutResult(status -> {
            entityManager.persist(first);
            entityManager.persist(second);
        });

        RxManager rxManager = mock(RxManager.class);
        AtomicBoolean refuseSecond = new AtomicBoolean(true);
        when(rxManager.archiveDrug(any(), anyInt(), anyInt(), anyString())).thenAnswer(invocation -> {
            int id = invocation.getArgument(1);
            if (id == second.getId() && refuseSecond.get()) throw new IllegalStateException("Synthetic archive failure");
            Drug row = drugDao.find(id);
            row.setArchived(true);
            row.setArchivedReason(Drug.REPRESCRIBED);
            drugDao.merge(row);
            return true;
        });
        var receipts = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.RxSubmissionReceiptService.class);
        org.mockito.Mockito.when(receipts.start(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        registerMock(io.github.carlos_emr.carlos.managers.RxSubmissionReceiptService.class, receipts);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        registerMock(RxManager.class, rxManager);
        registerMock(DrugDao.class, drugDao);
        registerMock(PlatformTransactionManager.class, transactions);

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1001);
        bean.setProviderNo("999998");
        var firstCard = card(first, 1);
        var secondCard = card(second, 2);
        bean.getStashList().addAll(List.of(firstCard, secondCard));
        bean.addRandomIdDrugIdPair(1, first.getId());
        bean.addRandomIdDrugIdPair(2, second.getId());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        try (var servlet = mockStatic(ServletActionContext.class);
             var _ = mockConstruction(RxPrescriptionData.class, (mock, context) ->
                     when(mock.saveScript(any(), any())).thenAnswer(invocation -> {
                         Prescription header = new Prescription();
                         header.setProviderNo("999998");
                         header.setDemographicId(1001);
                         entityManager.persist(header);
                         return header.getId().toString();
                     }))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new RxWriteScript2Action(mock(PrescriptionSignatureStampService.class));
            String firstRevision = firstCard.getDraftRevision();
            String secondRevision = secondCard.getDraftRevision();

            assertThatThrownBy(() -> action.persistStash(info, bean))
                    .isInstanceOf(RxWriteScript2Action.PrescriptionSaveException.class);

            template.executeWithoutResult(status -> {
                assertThat(count("Drug")).isEqualTo(2);
                assertThat(count("Prescription")).isZero();
                assertThat(entityManager.createQuery("select count(d) from Drug d where d.archived = false", Long.class)
                        .getSingleResult()).isEqualTo(2);
            });
            assertThat(firstCard.getDrugId()).isEqualTo(first.getId());
            assertThat(secondCard.getDrugId()).isEqualTo(second.getId());
            assertThat(firstCard.getScript_no()).isEqualTo("77");
            assertThat(firstCard.getDraftRevision()).isEqualTo(firstRevision);
            assertThat(secondCard.getDraftRevision()).isEqualTo(secondRevision);
            assertThat(bean.getRandomIdDrugIdPair()).containsEntry(1L, first.getId()).containsEntry(2L, second.getId());
            assertThat(request.getAttribute("scriptId")).isNull();
            // Strict audits are attempted inside the transaction. Their durable rollback
            // is covered by RxSaveReceiptCommitIntegrationTest, not by absence of a Java call.
            logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLogStrict(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString()), org.mockito.Mockito.atLeastOnce());

            refuseSecond.set(false);
            assertThat(action.persistStash(info, bean)).isNotBlank();
            template.executeWithoutResult(status -> {
                assertThat(count("Drug")).isEqualTo(4);
                assertThat(count("Prescription")).isEqualTo(1);
                assertThat(entityManager.createQuery("select count(d) from Drug d where d.archived = false", Long.class)
                        .getSingleResult()).isEqualTo(2);
                assertThat(drugDao.find(first.getId().intValue()).isArchived()).isTrue();
                assertThat(drugDao.find(second.getId().intValue()).isArchived()).isTrue();
            });
        } finally {
            template.executeWithoutResult(status -> {
                entityManager.createQuery("delete from Drug").executeUpdate();
                entityManager.createQuery("delete from Prescription").executeUpdate();
            });
        }
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    private static Drug original() {
        Drug drug = new Drug();
        drug.setProviderNo("999998");
        drug.setDemographicId(1001);
        drug.setSpecial("Synthetic original directions");
        drug.setPosition(1);
        return drug;
    }

    private static RxPrescriptionData.Prescription card(Drug original, long key) {
        var card = new RxPrescriptionData.Prescription(original.getId(), "999998", 1001);
        card.setRandomId(key);
        card.setGCN_SEQNO("0");
        card.setScript_no("77");
        card.setBrandName("Synthetic corrected drug");
        card.setSpecial("1 tab PO BID");
        card.setRxDate(java.sql.Date.valueOf("2026-01-01"));
        return card;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("rx_rollback");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase ownedDatabase) {
            return ownedDatabase.dataSource();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, IsolatedJpaTestDatabase ownedDatabase) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setManagedTypes(PersistenceManagedTypes.of(Drug.class.getName(), Prescription.class.getName(), PartialDate.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(ownedDatabase.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean DrugDao drugDao() { return new DrugDaoImpl(); }
        @Bean PartialDateDao partialDateDao() { return new PartialDateDaoImpl(); }
    }
}
