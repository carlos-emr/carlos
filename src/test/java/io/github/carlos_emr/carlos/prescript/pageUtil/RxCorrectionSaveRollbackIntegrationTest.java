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
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Uses real prescription/drug writes to verify a late correction failure cannot orphan an active row. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxCorrectionSaveRollbackIntegrationTest.Config.class)
@Tag("integration")
@Tag("prescription")
class RxCorrectionSaveRollbackIntegrationTest extends CarlosUnitTestBase {
    @Autowired private DrugDao drugDao;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldRollbackRowsAndWorkspace_thenRetryTheSameOriginals_whenSecondArchiveFails() {
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
             var data = mockConstruction(RxPrescriptionData.class, (mock, context) ->
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
            logActionMock.verifyNoInteractions();

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
        return drug;
    }

    private static RxPrescriptionData.Prescription card(Drug original, long key) {
        var card = new RxPrescriptionData.Prescription(original.getId(), "999998", 1001);
        card.setRandomId(key);
        card.setScript_no("77");
        card.setBrandName("Synthetic corrected drug");
        card.setSpecial("1 tab PO BID");
        return card;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:rx-correction;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setManagedTypes(PersistenceManagedTypes.of(Drug.class.getName(), Prescription.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean DrugDao drugDao() { return new DrugDaoImpl(); }
    }
}
