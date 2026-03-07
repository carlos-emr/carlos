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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.model.Billing;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
public class BillingmasterDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingmasterDAO dao;

    @Test
    @Tag("create")
    @DisplayName("should save and update billing unit for billing number")
    void shouldSaveAndUpdateBillingUnit_whenValidBillingProvided() {
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
    void shouldUpdateBillingUnit_whenBillingNumberExists() {
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
    void shouldMarkListAsBilled_whenBillingNumbersProvided() {
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
        dao.save(wcb);

        WCB found = dao.getWcbByBillingNo(999);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(wcb.getId());
        assertThat(found.getBilling_no()).isEqualTo(999);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null WCB when billing number does not exist")
    void shouldReturnNull_whenWcbBillingNoNotFound() {
        WCB found = dao.getWcbByBillingNo(999999);
        assertThat(found).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for various field combinations with no matching data")
    void shouldReturnEmptyList_whenNoMatchingBillingMasterData() {
        List<Object[]> results1 = dao.getBillingMasterByVariousFields("ST", null, null, null);
        assertThat(results1).isEmpty();

        List<Object[]> results2 = dao.getBillingMasterByVariousFields("ST", null, null, "01-01-2012");
        assertThat(results2).isEmpty();

        List<Object[]> results3 = dao.getBillingMasterByVariousFields("ST", null, "01-01-2011", "01-01-2012");
        assertThat(results3).isEmpty();

        List<Object[]> results4 = dao.getBillingMasterByVariousFields("ST", "01", null, null);
        assertThat(results4).isEmpty();

        List<Object[]> results5 = dao.getBillingMasterByVariousFields("ST", "01", "01-01-2011", "01-01-2012");
        assertThat(results5).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for WCB report when no matching data")
    void shouldReturnEmptyList_whenNoWcbReportData() {
        List<Object[]> results = dao.select_user_bill_report_wcb(1);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list for teleplan bill when no matching data")
    void shouldReturnEmptyList_whenNoTeleplanBillData() {
        List<Billing> results = dao.search_teleplanbill(1);
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no billings match demo, code, and statuses")
    void shouldReturnEmptyList_whenNoBillingsMatchDemoCodeStatuses() {
        List<Billingmaster> results = dao.findByDemoNoCodeAndStatuses(100, "10", Arrays.asList("A"));
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billings by demo number, code and statuses")
    void shouldReturnMatchingBillings_whenDemoNoCodeAndStatusesMatch() {
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

        entityManager.flush();

        // statuses is a NOT IN filter, so "A" excluded means b1 (status P) should be returned
        List<Billingmaster> results = dao.findByDemoNoCodeAndStatuses(200, "TESTCODE", Arrays.asList("A"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBillingmasterNo()).isEqualTo(b1.getBillingmasterNo());
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no billings match demo, code and year")
    void shouldReturnEmptyList_whenNoBillingsMatchDemoCodeYear() {
        List<Billingmaster> results = dao.findByDemoNoCodeStatusesAndYear(100, new Date(), "CODE");
        assertThat(results).isEmpty();
    }

    @Test
    @Tag("update")
    @DisplayName("should return zero when no billings match for update")
    void shouldReturnZero_whenNoBillingsMatchUpdateCriteria() {
        int count = dao.updateBillingUnitForBillingNumber("XX", 999999);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @Tag("update")
    @DisplayName("should return zero when marking empty list as billed")
    void shouldReturnZero_whenMarkingEmptyListAsBilled() {
        int count = dao.markListAsBilled(Arrays.asList());
        assertThat(count).isEqualTo(0);
    }
}
