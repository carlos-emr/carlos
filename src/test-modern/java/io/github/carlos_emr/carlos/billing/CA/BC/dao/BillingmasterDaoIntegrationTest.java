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

        assertThat(dao.getWcbByBillingNo(999)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return billing master by various field combinations")
    void shouldReturnResults_whenVariousFieldCombinationsProvided() {
        dao.getBillingMasterByVariousFields("ST", null, null, null);
        dao.getBillingMasterByVariousFields("ST", null, null, "01-01-2012");
        dao.getBillingMasterByVariousFields("ST", null, "01-01-2011", "01-01-2012");
        dao.getBillingMasterByVariousFields("ST", "01", null, null);
        dao.getBillingMasterByVariousFields("ST", "01", "01-01-2011", "01-01-2012");
    }

    @Test
    @Tag("read")
    @DisplayName("should return WCB report data for billing master number")
    void shouldReturnWcbReport_whenBillingMasterNoProvided() {
        List<Object[]> results = dao.select_user_bill_report_wcb(1);
        assertThat(results).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should search teleplan bill by billing master number")
    void shouldReturnTeleplanBill_whenBillingMasterNoProvided() {
        assertThat(dao.search_teleplanbill(1)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find by demo number, code and statuses")
    void shouldReturnResults_whenDemoNoCodeAndStatusesProvided() {
        assertThat(dao.findByDemoNoCodeAndStatuses(100, "10", Arrays.asList("A"))).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find by demo number, code, statuses and year")
    void shouldReturnResults_whenDemoNoCodeStatusesAndYearProvided() {
        assertThat(dao.findByDemoNoCodeStatusesAndYear(100, new Date(), "CODE")).isNotNull();
    }
}
