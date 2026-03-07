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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingONPaymentDao} covering full method coverage
 * matching the legacy {@code BillingONPaymentDaoTest}.
 *
 * <p>Tests cover find3rdPartyPayRecordsByBill (with and without date range).</p>
 *
 * @since 2026-03-07
 * @see BillingONPaymentDao
 */
@DisplayName("BillingONPayment Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class BillingONPaymentDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONPaymentDao dao;

    @Autowired
    private BillingONCHeader1Dao daoBONCH;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("read")
    @DisplayName("should return 3rd party pay records sorted by payment date")
    void shouldReturnPayRecordsSortedByDate_whenSearchingByBill() throws Exception {
        BillingONCHeader1 bONCHeader1 = new BillingONCHeader1();
        EntityDataGenerator.generateTestDataForModelClass(bONCHeader1);

        int billingNo = 1;

        BillingONPayment bONPayment1 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment1);
        Date date1 = new Date(dfm.parse("20110101").getTime());
        bONPayment1.setBillingNo(billingNo);
        bONPayment1.setPaymentDate(date1);

        BillingONPayment bONPayment2 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment2);
        Date date2 = new Date(dfm.parse("20110701").getTime());
        bONPayment2.setBillingNo(billingNo);
        bONPayment2.setPaymentDate(date2);

        BillingONPayment bONPayment3 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment3);
        Date date3 = new Date(dfm.parse("20110301").getTime());
        bONPayment3.setBillingNo(billingNo);
        bONPayment3.setPaymentDate(date3);

        daoBONCH.persist(bONCHeader1);
        dao.persist(bONPayment1);
        dao.persist(bONPayment2);
        dao.persist(bONPayment3);

        List<BillingONPayment> result = dao.find3rdPartyPayRecordsByBill(bONCHeader1);
        List<BillingONPayment> expectedResult = Arrays.asList(
                bONPayment1, bONPayment3, bONPayment2);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }

    @Test
    @Tag("read")
    @DisplayName("should return 3rd party pay records within date range sorted by payment date")
    void shouldReturnPayRecordsInDateRange_whenSearchingByBillAndDates() throws Exception {
        BillingONCHeader1 bONCHeader1 = new BillingONCHeader1();
        EntityDataGenerator.generateTestDataForModelClass(bONCHeader1);
        Date startDate = new Date(dfm.parse("20101230").getTime());
        Date endDate = new Date(dfm.parse("20120101").getTime());

        BillingONPayment bONPayment1 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment1);
        Date date1 = new Date(dfm.parse("20110102").getTime());
        bONPayment1.setBillingNo(1);
        bONPayment1.setPaymentDate(date1);

        BillingONPayment bONPayment2 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment2);
        Date date2 = new Date(dfm.parse("20110302").getTime());
        bONPayment2.setBillingNo(1);
        bONPayment2.setPaymentDate(date2);

        BillingONPayment bONPayment3 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment3);
        Date date3 = new Date(dfm.parse("20110502").getTime());
        bONPayment3.setBillingNo(1);
        bONPayment3.setPaymentDate(date3);

        BillingONPayment bONPayment4 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment4);
        Date date4 = new Date(dfm.parse("20090502").getTime());
        bONPayment4.setBillingNo(1);
        bONPayment4.setPaymentDate(date4);

        BillingONPayment bONPayment5 = new BillingONPayment();
        EntityDataGenerator.generateTestDataForModelClass(bONPayment5);
        Date date5 = new Date(dfm.parse("20130502").getTime());
        bONPayment5.setBillingNo(1);
        bONPayment5.setPaymentDate(date5);

        daoBONCH.persist(bONCHeader1);
        dao.persist(bONPayment1);
        dao.persist(bONPayment2);
        dao.persist(bONPayment3);

        List<BillingONPayment> result = dao.find3rdPartyPayRecordsByBill(bONCHeader1, startDate, endDate);
        List<BillingONPayment> expectedResult = Arrays.asList(
                bONPayment1, bONPayment2, bONPayment3);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
