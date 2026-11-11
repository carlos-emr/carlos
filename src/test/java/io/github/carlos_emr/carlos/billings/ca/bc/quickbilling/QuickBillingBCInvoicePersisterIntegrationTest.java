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
package io.github.carlos_emr.carlos.billings.ca.bc.quickbilling;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingHistory;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingNotes;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

/** Verifies the real transaction proxy rolls back every invoice table after a late note failure. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = QuickBillingBCInvoicePersisterIntegrationTest.Config.class)
@Tag("integration")
@Tag("billing")
@Tag("create")
class QuickBillingBCInvoicePersisterIntegrationTest {
    @Autowired private QuickBillingBCInvoicePersister persister;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from BillingNotes").executeUpdate();
            entityManager.createQuery("delete from BillingHistory").executeUpdate();
            entityManager.createQuery("delete from Billingmaster").executeUpdate();
            entityManager.createQuery("delete from Billing").executeUpdate();
        });
    }

    @Test
    void shouldCommitHeaderLinesHistoryAndNotes_forMultiLineInvoice() {
        Billing header = header();
        persister.saveInvoice(header, List.of(line("00100", "33.70"), line("00101", "15.00")),
                "999998", "Quick billing fixture");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(count("Billing")).isEqualTo(1);
            assertThat(count("Billingmaster")).isEqualTo(2);
            assertThat(count("BillingHistory")).isEqualTo(2);
            assertThat(count("BillingNotes")).isEqualTo(2);
            assertThat(entityManager.createQuery("from Billingmaster", Billingmaster.class).getResultList())
                    .allSatisfy(line -> assertThat(line.getBillingNo()).isEqualTo(header.getId()));
            assertThat(entityManager.createQuery("from BillingHistory", BillingHistory.class).getResultList())
                    .allSatisfy(history -> {
                        assertThat(history.getPaymentTypeId()).isEqualTo(6);
                        assertThat(history.getBillingType()).isEqualTo("MSP");
                        assertThat(history.getPractitionerNo()).isEqualTo("999998");
                    });
        });
    }

    @Test
    void shouldPreserveNotePunctuationAndFlattenLineBreaks_whenInvoiceCommits() {
        persister.saveInvoice(header(), List.of(line("00100", "33.70")), "999998",
                "Patient's note: C:\\claims\\draft\r\nSecond line\nFinal line");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            BillingNotes note = entityManager.createQuery("from BillingNotes", BillingNotes.class)
                    .getSingleResult();
            assertThat(note.getNote()).isEqualTo("Patient's note: C:\\claims\\draft  Second line Final line");
        });
    }

    @Test
    void shouldRollbackAllInvoiceTables_whenNoteWriteFailsAfterClaimsAndHistory() {
        // provider_no is NOT NULL. The failure happens after header, claim and history inserts.
        assertThatThrownBy(() -> persister.saveInvoice(header(), List.of(line("00100", "33.70")),
                null, "Quick billing fixture")).isInstanceOf(RuntimeException.class);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(count("Billing")).isZero();
            assertThat(count("Billingmaster")).isZero();
            assertThat(count("BillingHistory")).isZero();
            assertThat(count("BillingNotes")).isZero();
        });

        // A rebuilt queued invoice can be retried without a duplicate or orphan from the failed attempt.
        persister.saveInvoice(header(), List.of(line("00100", "33.70")), "999998", "Retry fixture");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(count("Billing")).isEqualTo(1);
            assertThat(count("Billingmaster")).isEqualTo(1);
            assertThat(count("BillingHistory")).isEqualTo(1);
            assertThat(count("BillingNotes")).isEqualTo(1);
        });
    }

    private long count(String entityName) {
        return entityManager.createQuery("select count(e) from " + entityName + " e", Long.class).getSingleResult();
    }

    private static Billing header() {
        Billing header = new Billing();
        header.setProviderNo("999998");
        header.setBillingtype("MSP");
        header.setStatus("O");
        header.setTotal("48.70");
        return header;
    }

    private static Billingmaster line(String code, String amount) {
        Billingmaster line = new Billingmaster();
        line.setBillingCode(code);
        line.setBillAmount(amount);
        line.setBillingstatus("O");
        line.setPaymentMethod(6);
        return line;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:quick-billing-invoice;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setManagedTypes(PersistenceManagedTypes.of(Billing.class.getName(), Billingmaster.class.getName(),
                    BillingHistory.class.getName(), BillingNotes.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        QuickBillingBCInvoicePersister quickBillingBCInvoicePersister() {
            return new QuickBillingBCInvoicePersister();
        }
    }
}
