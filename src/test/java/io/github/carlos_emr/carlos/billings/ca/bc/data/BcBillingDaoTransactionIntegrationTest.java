/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.LogTeleplanTx;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.ExtractBean;
import io.github.carlos_emr.carlos.billings.ca.bc.service.BcBillingService;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real commit/rollback checks, including a failure after generated log and status writes. */
@Tag("integration")
@Tag("dao")
@ContextConfiguration(inheritLocations = false, locations = "classpath:test-bc-billing-context.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BcBillingDaoTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private BcBillingService service;
    @Autowired @Qualifier("billingmasterDAO") private BillingmasterDAO masters;
    @Autowired private LogTeleplanTxDao logDao;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    private int billId;
    private int masterId;
    private Integer logId;
    private boolean published;

    @BeforeEach
    void persistOpenBill() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Billing bill = new Billing();
            bill.setStatus("O");
            bill.setProviderNo("BC54");
            entityManager.persist(bill);
            billId = bill.getId();
            Billingmaster master = new Billingmaster();
            master.setBillingNo(billId);
            master.setBillingstatus("O");
            entityManager.persist(master);
            masterId = master.getBillingmasterNo();
        });
    }

    @AfterEach
    void removeCommittedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (logId != null) entityManager.createQuery("delete from LogTeleplanTx l where l.id = :id")
                    .setParameter("id", logId).executeUpdate();
            entityManager.createQuery("delete from Billingmaster b where b.billingmasterNo = :id")
                    .setParameter("id", masterId).executeUpdate();
            entityManager.createQuery("delete from Billing b where b.id = :id")
                    .setParameter("id", billId).executeUpdate();
        });
    }

    private ExtractBean extract(boolean fail) {
        ExtractBean extract = new ExtractBean() {
            @Override
            public void dbQuery() {
                LogTeleplanTx log = new LogTeleplanTx();
                log.setClaim(new byte[]{1, 2, 3});
                logDao.persist(log);
                logId = log.getId();
                assertThat(logId).isPositive();
                service.markBillBilled(billId);
                service.markMasterBilled(masterId);
                assertThat(masters.findBcExtractClaims(billId)).isEmpty();
                assertThat(published).isFalse();
                entityManager.flush();
                // A separate physical connection must still see the earlier committed status.
                assertThat(committedMasterStatus()).isEqualTo("O");
                if (fail) throw new IllegalStateException("synthetic failure after earlier writes");
            }

            @Override
            public void writeHtml(String html) {
                published = true;
            }
        };
        extract.seteFlag("1");
        return extract;
    }

    @Test
    void shouldLoadClaimComment_forTheLegacyExtractProjection() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("update Billingmaster b set b.claimComment = :comment, b.correspondenceCode = '0' "
                        + "where b.billingmasterNo = :id").setParameter("comment", "FAKE CLAIM COMMENT").setParameter("id", masterId)
                        .executeUpdate());

        // The legacy export writes P58 from this projection; the column used to be missing from its SELECT.
        assertThat(masters.findBcExtractClaims(billId)).singleElement().satisfies(row -> {
            assertThat(row.billingmasterNo()).isEqualTo(masterId);
            assertThat(row.correspondenceCode()).isEqualTo("0");
            assertThat(row.claimComment()).isEqualTo("FAKE CLAIM COMMENT");
        });
        assertThat(masters.findBcPendingExtractClaims(billId)).singleElement()
                .satisfies(row -> assertThat(row.claimComment()).isEqualTo("FAKE CLAIM COMMENT"));
    }

    @Test
    void shouldRejectUnproxiedExtract_beforeAnyLogOrStatusWrite() {
        var unproxied = new BcBillingService(masters);
        assertThatThrownBy(() -> unproxied.generateExtract(extract(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A transaction is required to generate a BC billing extract");
        assertThat(logId).isNull();
        assertThat(committedMasterStatus()).isEqualTo("O");
        assertThat(published).isFalse();
    }

    @Test
    void shouldCommitGeneratedLogAndBothStatuses_withoutPublishingHtml() {
        service.generateExtract(extract(false));
        assertCommittedState("B", 1);
        assertThat(published).isFalse();
    }

    @Test
    void shouldRollbackGeneratedLogAndBothStatuses_whenLaterWorkFails() {
        assertThatThrownBy(() -> service.generateExtract(extract(true)))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after earlier writes");
        assertCommittedState("O", 0);
        assertThat(published).isFalse();
    }

    @Test
    void shouldRetainLegacyRequiresNewReads_whenCallerHasUncommittedStatuses() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.markMasterBilled(masterId);
            entityManager.flush();
            assertThat(masters.findBcExtractClaims(billId)).isEmpty();
            // Legacy public entry points suspend this transaction and see the committed row.
            assertThat(masters.getBillingmaster(masterId).getBillingstatus()).isEqualTo("O");
            assertThat(masters.getBillingmaster(String.valueOf(masterId)).getBillingstatus()).isEqualTo("O");
            assertThat(masters.getBillingMasterByBillingMasterNo(String.valueOf(masterId)).getBillingstatus()).isEqualTo("O");
            assertThat(masters.getBillingmasterByBillingNo(billId)).extracting(Billingmaster::getBillingstatus)
                    .containsExactly("O");
            assertThat(masters.getBillingMasterByBillingNo(String.valueOf(billId)))
                    .extracting(Billingmaster::getBillingstatus).containsExactly("O");
            status.setRollbackOnly();
        });
        assertThat(committedMasterStatus()).isEqualTo("O");
    }

    private void assertCommittedState(String expected, int logs) {
        JdbcTemplate observer = new JdbcTemplate(dataSource);
        assertThat(observer.queryForObject("select status from billing where billing_no=?", String.class, billId))
                .isEqualTo(expected);
        assertThat(observer.queryForObject("select billingstatus from billingmaster where billingmaster_no=?", String.class, masterId))
                .isEqualTo(expected);
        assertThat(observer.queryForObject("select count(*) from log_teleplantx where log_no=?", Integer.class, logId))
                .isEqualTo(logs);
    }

    private String committedMasterStatus() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select billingstatus from billingmaster where billingmaster_no=?")) {
            statement.setInt(1, masterId);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
    }
}
