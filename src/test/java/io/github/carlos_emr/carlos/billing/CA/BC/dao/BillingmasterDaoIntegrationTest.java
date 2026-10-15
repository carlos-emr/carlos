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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.model.Billing;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingmasterDAO}.
 * <p>Migrated from legacy JUnit 4 BillingmasterDAOTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("BillingmasterDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
@Isolated("Installs and removes the H2 TO_DAYS dialect adapter")
public class BillingmasterDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingmasterDAO dao;

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    private Integer fixtureBillingId;
    private Integer fixtureMasterId;

    /**
     * Test-only adapter for MySQL TO_DAYS ordering. The query compares day ordinals,
     * never their absolute epoch. Both ISO input dates and stored MSP yyyyMMdd dates
     * are accepted; vendor behavior remains covered by the BC database/browser checks.
     */
    public static Long toDays(String value) {
        if (value == null) return null;
        String isoDate = value.matches("[0-9]{8}")
                ? value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8)
                : value;
        try {
            return java.time.LocalDate.parse(isoDate).toEpochDay();
        } catch (java.time.format.DateTimeParseException invalidDate) {
            // MySQL TO_DAYS returns NULL for invalid legacy values, so unrelated
            // generated fixture rows must not turn a date-filter query into an error.
            return null;
        }
    }

    @BeforeEach
    void createDateDialectAdapter() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS TO_DAYS FOR "
                    + "'io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingmasterDaoIntegrationTest.toDays'");
        }
    }

    @AfterEach
    void removeDateDialectAdapterAndCommittedFixtures() throws Exception {
        // This DAO uses REQUIRES_NEW, so explicitly remove only this test's committed rows.
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            if (fixtureMasterId != null) statement.executeUpdate("DELETE FROM billingmaster WHERE billingmaster_no=" + fixtureMasterId);
            if (fixtureBillingId != null) statement.executeUpdate("DELETE FROM billing WHERE billing_no=" + fixtureBillingId);
            statement.execute("DROP ALIAS IF EXISTS TO_DAYS");
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should save and update billing unit for billing number")
    void shouldSaveAndUpdateBillingUnit_whenValidBillingProvided() throws Exception {
        Billingmaster master = new Billingmaster();
        EntityDataGenerator.generateTestDataForModelClass(master);
        master.setBillingNo(99999);
        dao.save(master);

        int count = dao.updateBillingUnitForBillingNumber("NIHRENASEIBE", 99999);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Tag("update")
    @DisplayName("should update billing unit for existing billing number")
    void shouldUpdateBillingUnit_whenBillingNumberExists() throws Exception {
        Billingmaster b = new Billingmaster();
        b.setBillingUnit("AS");
        b.setBillingNo(999);
        dao.save(b);

        int i = dao.updateBillingUnitForBillingNumber("BU", 999);
        assertThat(i).isEqualTo(1);
    }

    @Test
    @Tag("update")
    @DisplayName("should mark list of billings as billed")
    void shouldMarkListAsBilled_whenBillingNumbersProvided() throws Exception {
        Billingmaster b = new Billingmaster();
        b.setBillingUnit("AS");
        b.setBillingNo(999);
        dao.save(b);

        int i = dao.markListAsBilled(Arrays.asList(String.valueOf(b.getBillingmasterNo())));
        assertThat(i).isEqualTo(1);
    }

    @Test
    @Tag("read")
    @DisplayName("should return WCB by billing number")
    void shouldReturnWcb_whenBillingNoExists() throws Exception {
        WCB wcb = new WCB();
        EntityDataGenerator.generateTestDataForModelClass(wcb);
        wcb.setBilling_no(999);
        // provider_no column is INTEGER (from Wcb entity DDL) so must be numeric
        wcb.setProvider_no("123");
        dao.save(wcb);

        WCB found = dao.getWcbByBillingNo(999);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(wcb.getId());
        assertThat(found.getBilling_no()).isEqualTo(999);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null WCB when billing number does not exist")
    void shouldReturnNull_whenWcbBillingNoNotFound() throws Exception {
        WCB found = dao.getWcbByBillingNo(999999);
        assertThat(found).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for various field combinations with no matching data")
    void shouldReturnEmptyList_whenNoMatchingBillingMasterData() throws Exception {
        List<Object[]> results1 = dao.getBillingMasterByVariousFields("ST", null, null, null);
        assertThat(results1).isEmpty();

        List<Object[]> results2 = dao.getBillingMasterByVariousFields("ST", null, null, "2012-01-01");
        assertThat(results2).isEmpty();

        List<Object[]> results3 = dao.getBillingMasterByVariousFields("ST", null, "2011-01-01", "2012-01-01");
        assertThat(results3).isEmpty();

        List<Object[]> results4 = dao.getBillingMasterByVariousFields("ST", "01", null, null);
        assertThat(results4).isEmpty();

        List<Object[]> results5 = dao.getBillingMasterByVariousFields("ST", "01", "2011-01-01", "2012-01-01");
        assertThat(results5).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should filter persisted billing rows by provider, status and exclusive service dates")
    void shouldFilterPersistedBilling_whenProviderStatusAndDateRangeChange() {
        Billing billing = new Billing();
        billing.setProviderNo("9164055");
        dao.save(billing);
        fixtureBillingId = billing.getId();
        Billingmaster master = new Billingmaster();
        master.setBillingNo(billing.getId());
        master.setBillingstatus("R6");
        master.setServiceDate("20260304");
        dao.save(master);
        fixtureMasterId = master.getBillingmasterNo();

        List<Object[]> results = dao.getBillingMasterByVariousFields("R6", "9164055", "2026-03-03", "2026-03-05");
        assertThat(results).hasSize(1);
        assertThat(((Number) results.get(0)[0]).intValue()).isEqualTo(fixtureBillingId);
        assertThat(((Number) results.get(0)[18]).intValue()).isEqualTo(fixtureMasterId);
        assertThat(dao.getBillingMasterByVariousFields("R6", "9164055", null, null)).hasSize(1);
        assertThat(dao.getBillingMasterByVariousFields("R6", "other-provider", null, null)).isEmpty();
        assertThat(dao.getBillingMasterByVariousFields("different-status", "9164055", null, null)).isEmpty();
        assertThat(dao.getBillingMasterByVariousFields("R6", "9164055", "2026-03-04", null)).isEmpty();
        assertThat(dao.getBillingMasterByVariousFields("R6", "9164055", null, "2026-03-04")).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for WCB report when no matching data")
    void shouldReturnEmptyList_whenNoWcbReportData() throws Exception {
        List<Object[]> results = dao.select_user_bill_report_wcb(1);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for teleplan bill when no matching data")
    void shouldReturnEmptyList_whenNoTeleplanBillData() throws Exception {
        List<Billing> results = dao.search_teleplanbill(1);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no billings match demo, code, and statuses")
    void shouldReturnEmptyList_whenNoBillingsMatchDemoCodeStatuses() throws Exception {
        List<Billingmaster> results = dao.findByDemoNoCodeAndStatuses(100, "10", Arrays.asList("A"));
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billings by demo number, code and statuses")
    void shouldReturnMatchingBillings_whenDemoNoCodeAndStatusesMatch() throws Exception {
        Billingmaster b1 = new Billingmaster();
        EntityDataGenerator.generateTestDataForModelClass(b1);
        b1.setDemographicNo(200);
        b1.setBillingCode("TESTCODE");
        b1.setBillingstatus("P");
        b1.setBillingNo(1001);
        dao.save(b1);

        Billingmaster b2 = new Billingmaster();
        EntityDataGenerator.generateTestDataForModelClass(b2);
        b2.setDemographicNo(200);
        b2.setBillingCode("TESTCODE");
        b2.setBillingstatus("A");
        b2.setBillingNo(1002);
        dao.save(b2);

        hibernateTemplate.flush();

        // statuses is a NOT IN filter, so "A" excluded means b1 (status P) should be returned
        List<Billingmaster> results = dao.findByDemoNoCodeAndStatuses(200, "TESTCODE", Arrays.asList("A"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBillingmasterNo()).isEqualTo(b1.getBillingmasterNo());
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no billings match demo, code and year")
    void shouldReturnEmptyList_whenNoBillingsMatchDemoCodeYear() throws Exception {
        List<Billingmaster> results = dao.findByDemoNoCodeStatusesAndYear(100, new Date(), "CODE");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("update")
    @DisplayName("should return zero when no billings match for update")
    void shouldReturnZero_whenNoBillingsMatchUpdateCriteria() throws Exception {
        int count = dao.updateBillingUnitForBillingNumber("XX", 999999);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @Tag("update")
    @DisplayName("should return zero when marking empty list as billed")
    void shouldReturnZero_whenMarkingEmptyListAsBilled() throws Exception {
        int count = dao.markListAsBilled(Arrays.asList());
        assertThat(count).isEqualTo(0);
    }
}
