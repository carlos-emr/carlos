/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.entities.MSPBill;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Native date/decimal/report behavior against a fresh physical BC Flyway schema. */
@Tag("integration")
@Tag("dao")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_PROVINCE", matches = "bc")
class BcBillingNativeMariaDbIntegrationTest extends CarlosTestBase {
    @Autowired @Qualifier("billingmasterDAO") private BillingmasterDAO dao;
    @Autowired private BillingServiceDao billingServiceDao;
    @Autowired private DataSource dataSource;
    @jakarta.persistence.PersistenceContext(unitName = "entityManagerFactory")
    private jakarta.persistence.EntityManager entityManager;
    private JdbcTemplate jdbc;
    private static final int BILL = 9554601;
    private static final int MASTER = 9554601;

    @BeforeEach
    void seedOwnedBcRows() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into provider (provider_no, first_name, last_name, ohip_no, billing_no, provider_type, status, lastUpdateDate) values (?, ?, ?, ?, ?, 'doctor', '1', now())",
                "BC54", "FAKE", "Provider5546", "91234", "81234");
        jdbc.update("insert into provider (provider_no, first_name, last_name, ohip_no, billing_no, provider_type, status, lastUpdateDate) values (?, ?, ?, ?, ?, 'doctor', '1', now())",
                "BC55", "FAKE", "Payee5546", "81234", "81234");
        jdbc.update("insert into demographic (demographic_no, first_name, last_name) values (?, ?, ?)",
                985546, "BC", "FAKE-5546");
        jdbc.update("insert into billing (billing_no, provider_no, apptProvider_no, provider_ohip_no, demographic_no, demographic_name, billingtype, status, billing_date, visitdate) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BILL, "BC54", "BC54", "91234", 985546, "FAKE-5546,BC", "MSP", "O", "2026-10-15", "2026-10-15");
        master(MASTER, "O", "20261015", "10.00");
    }

    private void master(int id, String status, String date, String amount) {
        jdbc.update("insert into billingmaster (billingmaster_no, billing_no, billingstatus, service_date, bill_amount, payee_no, practitioner_no, datacenter) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, BILL, status, date, amount, "81234", "91234", "00001");
    }

    @Test
    void shouldMatchPackagedSqlSettings_forApplicationConnections() {
        assertThat(jdbc.queryForObject("select @@SESSION.innodb_strict_mode", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select @@SESSION.sql_mode", String.class)).isEmpty();
    }

    @Test
    void shouldRetainNumericTimeRules_fromPhysicalTinyintFlags() {
        jdbc.update("insert into billing_msp_servicecode_times (billingservice_no, timeRange) values (?, ?)",
                "5694START", 0);
        jdbc.update("insert into billing_msp_servicecode_times (billingservice_no, timeRange) values (?, ?)",
                "5694RANGE", 1);

        assertThat(billingServiceDao.findTimeRules()).contains(
                new BillingServiceDao.TimeRule("5694START", "0"),
                new BillingServiceDao.TimeRule("5694RANGE", "1"));
    }

    @Test
    void shouldPreserveCharacterStatusAndCallerWrites_whenReadingCurrentBillState() {
        assertThat(dao.findBillStatus(String.valueOf(MASTER))).isEqualTo("O");
        var state = dao.findCurrentBillState(String.valueOf(MASTER));
        assertThat(state.practitionerNo()).isEqualTo("BC54");
        assertThat(state.billingType()).isEqualTo("MSP");
        assertThat(state.status()).isEqualTo("O");
        assertThat(state.amount()).isEqualTo("10.00");
        jdbc.update("UPDATE billingmaster SET billingstatus='P' WHERE billingmaster_no=?", MASTER);
        assertThat(dao.findBillStatus(String.valueOf(MASTER))).isEqualTo("P");
        assertThat(dao.findCurrentBillState(String.valueOf(MASTER)).status()).isEqualTo("P");
        assertThat(dao.findBillStatus(String.valueOf(MASTER + 1))).isNull();
        assertThat(dao.findCurrentBillState(String.valueOf(MASTER + 1))).isNull();
    }

    @Test
    void shouldPreserveProviderStatusDateAndServiceOrder_forInvoiceReads() {
        master(MASTER + 1, "O", "20261001", "12.30");
        master(MASTER + 2, "D", "20261010", "5.00");
        assertThat(dao.findBcBills("BC54", "812%", "BC54", "2026-10-01", "2026-10-15",
                false, false, false, false, MSPReconcile.REP_INVOICE))
                .extracting(BcBillingRows.BillRow::billingmasterNo)
                .containsExactly(String.valueOf(MASTER + 1), String.valueOf(MASTER));
        assertThat(dao.findBcBills("BC54", "81234", "other", "", "",
                false, false, false, false, MSPReconcile.REP_INVOICE)).isEmpty();
        assertThat(dao.findBcBills("BC54", "81234", "BC54", "20261015", "20261015",
                false, false, false, false, MSPReconcile.REP_INVOICE))
                .extracting(BcBillingRows.BillRow::billAmount).containsExactly("10.00");
        assertThat(dao.findBcBills("BC54", "81234", "BC54", "", "",
                false, true, false, false, MSPReconcile.REP_INVOICE)).isEmpty();
    }

    @Test
    void shouldPreservePrivatePaymentSignsAndDateBounds_withoutInternalAdjustments() {
        jdbc.update("update billing set billingtype='Pri' where billing_no=?", BILL);
        jdbc.update("insert into billing_history (billingmaster_no, creation_date, amount_received, payment_type_id) values (?, ?, ?, ?)",
                MASTER, "2026-10-15 23:59:59", "-12.34", 1);
        jdbc.update("insert into billing_history (billingmaster_no, creation_date, amount_received, payment_type_id) values (?, ?, ?, ?)",
                MASTER, "2026-10-15 12:00:00", "12.30", 10);
        var rows = dao.findBcPrivatePayments("BC54", "81234", "BC54", "20261015", "20261015");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.amountReceived()).isEqualTo("-12.34");
            assertThat(row.creationDate().toString()).isEqualTo("2026-10-15");
        });
        var report = new MSPReconcile(true).getPrivatePayments("BC54", "81234", "BC54", "2026-10-15", "2026-10-15");
        assertThat(report.list).singleElement().satisfies(value -> {
            MSPBill bill = (MSPBill) value;
            assertThat(bill.amount).isEqualTo("-12.34");
            assertThat(bill.paymentDate).isEqualTo("20261015");
            assertThat(bill.type).isEqualTo("RFD");
        });
        jdbc.update("update billingmaster set billingstatus='D' where billingmaster_no=?", MASTER);
        assertThat(dao.findBcPrivatePayments("BC54", "81234", "BC54", "", "")).isEmpty();
    }

    private void remittance(String paid, String paymentDate) {
        jdbc.update("insert into teleplanS00 (s21_id, t_officeno, t_practitionerno, t_payeeno, t_payment, t_paidamt, t_dataseq) values (?, ?, ?, ?, ?, ?, ?)",
                95546, String.valueOf(MASTER), "91234", "81234", paymentDate, paid, "0000042");
    }

    @Test
    void shouldKeepOverpunchedRefundsAndProviderRestriction_forPayments() {
        remittance("000123M", "20261016");
        var report = new MSPReconcile(true).getPayments("BC54", "81234", "BC54", "2026-10-16", "2026-10-16",
                false, false, true, false);
        assertThat(report.list).singleElement().satisfies(value -> {
            MSPBill bill = (MSPBill) value;
            assertThat(bill.amount).isEqualTo("-12.34");
            assertThat(bill.type).isEqualTo("RFD");
            assertThat(bill.seqNum).isEqualTo("0000042");
        });
        assertThatThrownBy(() -> dao.findBcPayments("BC54", "81234", "missing", "", "", false, false, true, false))
                .isInstanceOf(BillingmasterDAO.MissingBcPaymentProviderException.class).hasMessage("Provider must have ohip no!");
        assertThatThrownBy(() -> new MSPReconcile().getPayments("BC54", "81234", "missing", "", "", false, false, true, false))
                .isInstanceOf(BillingmasterDAO.MissingBcPaymentProviderException.class).hasMessage("Provider must have ohip no!");
        assertThatThrownBy(() -> new MSPReconcile(true).getPayments("BC54", "81234", "missing", "", "", false, false, true, false))
                .isInstanceOf(BillingmasterDAO.MissingBcPaymentProviderException.class).hasMessage("Provider must have ohip no!").hasNoCause();
        assertThat(dao.findBcPayments("other", "81234", "BC54", "", "", false, false, true, false)).isEmpty();
    }

    @Test
    void shouldKeepNativeMoneyAndNullableFields_forRemittance() {
        remittance("001001", "20261016");
        assertThat(dao.findBcRemittance("812%", "95546")).singleElement().satisfies(row -> {
            assertThat(row.demographic_no()).isEqualTo(985546);
            assertThat(row.t_servicedate()).isEqualTo("20261015");
            assertThat(row.t_exp1()).isNull();
        });
        assertThat(dao.findBcRemittance("other", "95546")).isEmpty();
    }

    @Test
    void shouldReadWcbFeesAndNullableFields_throughTheExistingMapping() {
        jdbc.update("insert into wcb (billing_no, bill_amount, w_feeitem, w_icd9) values (?, ?, ?, ?)",
                BILL, "45.67", "00100", "250");
        assertThat(dao.findBcWcb(String.valueOf(BILL)))
                .isEqualTo(new BcBillingRows.WcbRow("45.67", "00100", "250"));
        assertThat(dao.findBcWcb("0")).isNull();
    }

    @Test
    void shouldRetainExtractOhipAndStatusRestriction_withBoundDates() {
        assertThat(dao.findBcExtractBills("91234", "and billing_date >= ? and visitdate <= ?",
                new String[]{"2026-10-15", "2026-10-15"})).hasSize(1);
        assertThat(dao.findBcExtractBills("BC54", "", new String[0])).isEmpty();
        jdbc.update("update billingmaster set demographic_no=NULL, appointment_no=NULL where billingmaster_no=?", MASTER);
        assertThat(dao.findBcExtractClaims(BILL)).singleElement().satisfies(row -> {
            assertThat(row.billingmasterNo()).isEqualTo(MASTER);
            assertThat(row.billAmount()).isEqualTo("10.00");
        });
        dao.markBcMasterBilled(MASTER);
        dao.markBcBillBilled(BILL);
        assertThat(dao.findBcExtractClaims(BILL)).isEmpty();
        assertThat(dao.findBcExtractBills("91234", "", new String[0])).isEmpty();
        assertThatThrownBy(() -> dao.findBcExtractBills("91234", "or 1=1", new String[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Autowired private io.github.carlos_emr.carlos.billing.CA.dao.WcbBpCodeDao bodyParts;

    @Test
    void shouldListInstalledBodyParts_whenSearchIsEmpty() {
        jdbc.update("INSERT INTO wcb_bp_code (code,level1,usagenote) VALUES (?,?,?)", "TST54", "Synthetic reference", "Synthetic UTF-8 note: café");
        var rows = bodyParts.findByCodeOrAnyLevel("%%");
        assertThat(rows).isNotEmpty();
        assertThat(rows).filteredOn(row -> "TST54".equals(row.getCode()))
                .singleElement().satisfies(row -> assertThat(row.getUsagenote()).isEqualTo("Synthetic UTF-8 note: café"));
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getCode()).isNotBlank();
            row.getUsagenote();
        });
    }

    @Test
    void shouldReadOnlyReportPhoneFields_forSelectedPatients() {
        jdbc.update("UPDATE demographic SET phone=?,phone2=? WHERE demographic_no=?", "0012345", "0098765", 985546);
        assertThat(dao.findReportPhones(java.util.Arrays.asList("985546", "985546", "999999", null, "", "bad", "999999999999999999999")))
                .containsEntry("985546", new BillingmasterDAO.PatientPhones("0012345", "0098765"))
                .doesNotContainKey("999999");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"123456.78", "-12345.67", "badvalue9", " ", "12.34 "})
    void shouldArchiveEntireSourceAmount_withoutNumericCoercion(String source) {
        jdbc.update("UPDATE billing SET billingtype='Pri' WHERE billing_no=?", BILL);
        jdbc.update("UPDATE billingmaster SET bill_amount=?,paymentMethod='1' WHERE billingmaster_no=?", source, MASTER);
        new BillingHistoryDAO().createBillingHistoryArchive(String.valueOf(MASTER));
        assertThat(jdbc.queryForObject("SELECT amount FROM billing_history WHERE billingmaster_no=?", String.class, MASTER))
                .isEqualTo(source == null ? "" : source);
    }

    @Test
    void shouldPreserveAllCodeCharacters_whenBodyPartSearchIsWrapped() {
        jdbc.update("INSERT INTO wcb_bp_code (code,level1) VALUES ('TST54','Synthetic'),('TST55','Synthetic')");
        assertThat(bodyParts.findByCodeOrAnyLevel("%TST54%"))
                .extracting(io.github.carlos_emr.carlos.billing.CA.BC.model.WcbBpCode::getCode)
                .containsExactly("TST54");
    }

    @Test
    void shouldTreatLegacyWcbZeroDatesAsMissing_withoutRewritingThemOnAnUnrelatedEdit() {
        jdbc.update("INSERT INTO wcb (id,billing_no,demographic_no,provider_no) VALUES (95546,?,?,?)", BILL, 985546, "BC54");
        // Invoke the query implementation within this test's transaction so the owned uncommitted fixture is visible.
        BillingmasterDAO reader = org.springframework.test.util.AopTestUtils.getTargetObject(dao);
        var form = reader.getWCBForm("95546");
        assertThat(form.getFormCreated()).isNull();
        assertThat(form.getFormEdited()).isNull();
        assertThat(form.getW_dob()).isNull();
        assertThat(form.getW_doi()).isNull();
        assertThat(form.getW_servicedate()).isNull();
        assertThat(reader.getWCBForms(985546)).hasSize(1);
        assertThat(reader.findBcWcb(String.valueOf(BILL))).isNotNull();
        form.setW_problem("Synthetic unrelated edit");
        entityManager.merge(form);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT CAST(w_doi AS CHAR) FROM wcb WHERE id=95546", String.class)).isEqualTo("0000-00-00");
        assertThat(jdbc.queryForObject("SELECT CAST(formCreated AS CHAR) FROM wcb WHERE id=95546", String.class)).isEqualTo("0000-00-00 00:00:00");
    }

    @Test
    void shouldRetainPrivatePayments_whenLegacyCreationDateIsZero() {
        jdbc.update("UPDATE billing SET billingtype='Pri' WHERE billing_no=?", BILL);
        jdbc.update("INSERT INTO billing_history (billingmaster_no,billingstatus,amount_received,payment_type_id) VALUES (?,'P','12.34',1)", MASTER);
        var rows = dao.findBcPrivatePayments("BC54", "81234", "BC54", "", "");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().creationDate()).isNull();
    }

    @Autowired private io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao historyDao;

    @Test
    void shouldKeepAllHistoryTotalsConsistent_whenAmountsRequireExactSummationOrAreCorrupt() {
        for (String amount : java.util.List.of("0.10", "0.20", "")) {
            jdbc.update("INSERT INTO billing_history (billingmaster_no,amount_received,payment_type_id) VALUES (?,?,1)", MASTER, amount);
        }
        assertThat(historyDao.getTotalPaidFromHistory(MASTER, false)).isEqualTo(0.30);
        assertThat(historyDao.getTotalPaidFromHistoryByBillingMasterNos(java.util.List.of(MASTER))).containsEntry(MASTER, 0.30);
        assertThat(historyDao.getPaymentTotalsByBillingMasterNos(java.util.List.of(MASTER)).get(MASTER).excludingAdjustments()).isEqualTo(0.30);
        jdbc.update("INSERT INTO billing_history (billingmaster_no,amount_received,payment_type_id) VALUES (?,?,'10')", MASTER, "bad");
        assertThat(historyDao.getTotalPaidFromHistory(MASTER, false)).isNaN();
        assertThat(historyDao.getTotalPaidFromHistoryByBillingMasterNos(java.util.List.of(MASTER)).get(MASTER)).isNaN();
        var totals = historyDao.getPaymentTotalsByBillingMasterNos(java.util.List.of(MASTER)).get(MASTER);
        assertThat(totals.includingAdjustments()).isNaN();
        assertThat(totals.excludingAdjustments()).isEqualTo(0.30);
    }

}
