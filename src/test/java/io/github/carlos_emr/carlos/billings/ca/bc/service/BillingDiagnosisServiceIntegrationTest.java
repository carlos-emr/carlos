/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real commits and rollbacks, with verification on a separate physical connection. */
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@ContextConfiguration(locations = "classpath:test-billing-diagnosis-context.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingDiagnosisServiceIntegrationTest extends CarlosTestBase {
    private final int patient = 600000000 + (UUID.randomUUID().hashCode() & 0xfffffff);
    @Autowired private BillingDiagnosisService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("dataSource") private DataSource dataSource;
    @PersistenceContext private EntityManager entityManager;

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }

    private Billingmaster bill(int patientNo, String date, String status, String first, String second, String third) {
        Billingmaster bill = new Billingmaster();
        bill.setDemographicNo(patientNo);
        bill.setServiceDate(date);
        bill.setBillingstatus(status);
        bill.setDxCode1(first);
        bill.setDxCode2(second);
        bill.setDxCode3(third);
        entityManager.persist(bill);
        entityManager.flush();
        return bill;
    }

    @AfterEach
    void removeCommittedFixtures() {
        transaction().executeWithoutResult(tx -> entityManager.createQuery(
                "DELETE FROM Billingmaster b WHERE b.demographicNo IN (:patient, :other)")
                .setParameter("patient", patient).setParameter("other", patient + 1).executeUpdate());
    }

    @Test
    void shouldLimitBillsBeforeExpandingCodes_whenHistoryContainsDeletedAndOtherPatientRows() {
        transaction().executeWithoutResult(tx -> {
            for (int day = 1; day <= 11; day++) {
                bill(patient, "202610%02d".formatted(day), "O", "same", "d" + day, null);
            }
            bill(patient, "20261101", "D", "del", null, null);
            bill(patient, "20261102", null, "null", null, null);
            bill(patient + 1, "20261103", "O", "other", null, null);
        });
        assertThat(service.findRecentDiagnoses(String.valueOf(patient)))
                .extracting(BillingDiagnosisService.DiagnosisDto::code)
                .containsExactly("same", "d11", "d10", "d9", "d8", "d7", "d6", "d5", "d4", "d3", "d2");
    }

    @Test
    void shouldBindQuotedCodesAndKeepNulls_whenColumnsContainLegacyValues() {
        transaction().executeWithoutResult(tx -> bill(patient, null, "O", "a'b", "  ", null));
        assertThat(service.findRecentDiagnoses(String.valueOf(patient)))
                .containsExactly(new BillingDiagnosisService.DiagnosisDto(null, "a'b", null));
    }

    @Test
    void shouldJoinCallerTransactionAndCommitBothWrites_whenReadOccursBeforeSecondWrite() throws Exception {
        int id = transaction().execute(tx -> {
            Billingmaster first = bill(patient, "20261101", "O", "first", null, null);
            assertThat(service.findRecentDiagnoses(String.valueOf(patient)))
                    .extracting(BillingDiagnosisService.DiagnosisDto::code).containsExactly("first");
            bill(patient, "20261102", "O", "next", null, null);
            return first.getBillingmasterNo();
        });
        assertThat(id).isPositive();
        assertThat(independentCount()).isEqualTo(2);
        assertThat(service.findRecentDiagnoses(String.valueOf(patient)))
                .extracting(BillingDiagnosisService.DiagnosisDto::code).containsExactly("next", "first");
    }

    @Test
    void shouldRollBackBothWrites_whenFailureFollowsReadAndSecondWrite() throws Exception {
        assertThatThrownBy(() -> transaction().executeWithoutResult(tx -> {
            bill(patient, "20261101", "O", "first", null, null);
            assertThat(service.findRecentDiagnoses(String.valueOf(patient)))
                    .extracting(BillingDiagnosisService.DiagnosisDto::code).containsExactly("first");
            bill(patient, "20261102", "O", "next", null, null);
            throw new IllegalStateException("synthetic failure after both writes");
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after both writes");
        assertThat(independentCount()).isZero();
        assertThat(service.findRecentDiagnoses(String.valueOf(patient))).isEmpty();
    }

    private int independentCount() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM billingmaster WHERE demographic_no=?")) {
            statement.setInt(1, patient);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getInt(1);
            }
        }
    }
}
