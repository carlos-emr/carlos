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
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.util.ConversionUtils;
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
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link RaDetailDao} covering full method coverage
 * matching the legacy {@code RaDetailDaoTest}.
 *
 * <p>Tests cover create, findByBillingNo, findByRaHeaderNo,
 * findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode,
 * getRaDetailByDate (both overloads), getRaDetailByClaimNo,
 * getBillingExplanatoryList, and simple query methods.</p>
 *
 * @since 2026-03-07
 * @see RaDetailDao
 */
@DisplayName("RaDetail Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class RaDetailDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private RaDetailDao dao;

    @Autowired
    private RaHeaderDao raHeaderDao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    // --- create test ---

    @Test
    @Tag("create")
    @DisplayName("should persist RaDetail with generated test data")
    void shouldPersistRaDetail_whenValidDataProvided() throws Exception {
        RaDetail d = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(d);
        dao.persist(d);
        hibernateTemplate.flush();

        assertThat(d).isNotNull();
        assertThat(d.getId()).isPositive();
    }

    // --- findByBillingNo test ---

    @Test
    @Tag("read")
    @DisplayName("should return RaDetails ordered by raHeaderNo desc when finding by billingNo")
    void shouldReturnRaDetails_byBillingNo() throws Exception {
        int billingNo1 = 101;
        int billingNo2 = 202;

        int raHeaderNo1 = 111;
        int raHeaderNo2 = 222;
        int raHeaderNo3 = 333;
        int raHeaderNo4 = 444;
        int raHeaderNo5 = 555;

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setBillingNo(billingNo1);
        raDetail1.setRaHeaderNo(raHeaderNo1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setBillingNo(billingNo2);
        raDetail2.setRaHeaderNo(raHeaderNo2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setBillingNo(billingNo1);
        raDetail3.setRaHeaderNo(raHeaderNo3);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setBillingNo(billingNo2);
        raDetail4.setRaHeaderNo(raHeaderNo4);
        dao.persist(raDetail4);

        RaDetail raDetail5 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail5);
        raDetail5.setBillingNo(billingNo1);
        raDetail5.setRaHeaderNo(raHeaderNo5);
        dao.persist(raDetail5);

        hibernateTemplate.flush();

        List<RaDetail> expectedResult = Arrays.asList(raDetail5, raDetail3, raDetail1);
        List<RaDetail> result = dao.findByBillingNo(billingNo1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- findByRaHeaderNo test ---

    @Test
    @Tag("read")
    @DisplayName("should return RaDetails when finding by raHeaderNo")
    void shouldReturnRaDetails_byRaHeaderNo() throws Exception {
        int raHeaderNo1 = 111;
        int raHeaderNo2 = 222;

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setRaHeaderNo(raHeaderNo1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setRaHeaderNo(raHeaderNo2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setRaHeaderNo(raHeaderNo1);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setRaHeaderNo(raHeaderNo2);
        dao.persist(raDetail4);

        RaDetail raDetail5 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail5);
        raDetail5.setRaHeaderNo(raHeaderNo1);
        dao.persist(raDetail5);

        hibernateTemplate.flush();

        List<RaDetail> expectedResult = Arrays.asList(raDetail1, raDetail3, raDetail5);
        List<RaDetail> result = dao.findByRaHeaderNo(raHeaderNo1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode test ---

    @Test
    @Tag("read")
    @DisplayName("should return unique billing numbers excluding specified error codes")
    void shouldReturnUniqueBillingNos_byRaHeaderNoAndProviderExcludingErrorCodes() throws Exception {
        int raHeaderNo1 = 111;
        int raHeaderNo2 = 222;

        int billingNo1 = 11;
        int billingNo2 = 12;
        int billingNo3 = 13;
        int billingNo4 = 14;
        int billingNo5 = 15;

        String providerOhipNo1 = "101";
        String providerOhipNo2 = "202";

        String errorCode1 = "al";
        String errorCode2 = "br";
        String errorCode3 = "ch";

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setRaHeaderNo(raHeaderNo1);
        raDetail1.setProviderOhipNo(providerOhipNo1);
        raDetail1.setErrorCode(errorCode1);
        raDetail1.setBillingNo(billingNo1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setRaHeaderNo(raHeaderNo2);
        raDetail2.setProviderOhipNo(providerOhipNo1);
        raDetail2.setErrorCode(errorCode2);
        raDetail2.setBillingNo(billingNo2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setRaHeaderNo(raHeaderNo1);
        raDetail3.setProviderOhipNo(providerOhipNo2);
        raDetail3.setErrorCode(errorCode3);
        raDetail3.setBillingNo(billingNo3);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setRaHeaderNo(raHeaderNo1);
        raDetail4.setProviderOhipNo(providerOhipNo1);
        raDetail4.setErrorCode(errorCode3);
        raDetail4.setBillingNo(billingNo4);
        dao.persist(raDetail4);

        RaDetail raDetail5 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail5);
        raDetail5.setRaHeaderNo(raHeaderNo1);
        raDetail5.setProviderOhipNo(providerOhipNo1);
        raDetail5.setErrorCode(errorCode3);
        raDetail5.setBillingNo(billingNo5);
        dao.persist(raDetail5);

        hibernateTemplate.flush();

        List<Integer> expectedResult = Arrays.asList(billingNo4, billingNo5);
        String codes = errorCode1 + "," + errorCode2;
        List<Integer> result = dao.findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode(raHeaderNo1, providerOhipNo1, codes);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- getRaDetailByDate(Date, Date, Locale) test ---

    @Test
    @Tag("read")
    @DisplayName("should return RaDetails within date range")
    void shouldReturnRaDetails_byDateRange() throws Exception {
        String paymentDate1 = "20130101";
        String paymentDate2 = "20120101";
        String paymentDate3 = "20110101";
        String paymentDate4 = "20100101";
        String paymentDate5 = "20080101";

        RaHeader raHeader1 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader1);
        raHeader1.setPaymentDate(paymentDate1);
        raHeaderDao.persist(raHeader1);

        RaHeader raHeader2 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader2);
        raHeader2.setPaymentDate(paymentDate2);
        raHeaderDao.persist(raHeader2);

        RaHeader raHeader3 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader3);
        raHeader3.setPaymentDate(paymentDate3);
        raHeaderDao.persist(raHeader3);

        RaHeader raHeader4 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader4);
        raHeader4.setPaymentDate(paymentDate4);
        raHeaderDao.persist(raHeader4);

        RaHeader raHeader5 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader5);
        raHeader5.setPaymentDate(paymentDate5);
        raHeaderDao.persist(raHeader5);

        int billingNo1 = 101;
        int billingNo2 = 202;

        Date startDate = new Date(dfm.parse("20090101").getTime());
        Date endDate = new Date(dfm.parse("20121215").getTime());
        Locale locale = Locale.getDefault();

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setRaHeaderNo(raHeader1.getId());
        raDetail1.setBillingNo(billingNo1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setRaHeaderNo(raHeader2.getId());
        raDetail2.setBillingNo(billingNo2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setRaHeaderNo(raHeader3.getId());
        raDetail3.setBillingNo(billingNo1);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setRaHeaderNo(raHeader4.getId());
        raDetail4.setBillingNo(billingNo1);
        dao.persist(raDetail4);

        RaDetail raDetail5 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail5);
        raDetail5.setRaHeaderNo(raHeader5.getId());
        raDetail5.setBillingNo(billingNo1);
        dao.persist(raDetail5);

        hibernateTemplate.flush();

        List<RaDetail> expectedResult = Arrays.asList(raDetail2, raDetail3, raDetail4);
        List<RaDetail> result = dao.getRaDetailByDate(startDate, endDate, locale);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- getRaDetailByDate(Provider, Date, Date, Locale) test ---

    @Test
    @Tag("read")
    @DisplayName("should return RaDetails within date range filtered by provider")
    void shouldReturnRaDetails_byProviderAndDateRange() throws Exception {
        String paymentDate1 = "20130101";
        String paymentDate2 = "20120101";
        String paymentDate3 = "20110101";
        String paymentDate4 = "20100101";
        String paymentDate5 = "20080101";

        String ohipNo1 = "101";
        String ohipNo2 = "202";

        Provider provider1 = new Provider();
        provider1.setOhipNo(ohipNo1);

        Provider provider2 = new Provider();
        provider2.setOhipNo(ohipNo2);

        RaHeader raHeader1 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader1);
        raHeader1.setPaymentDate(paymentDate1);
        raHeaderDao.persist(raHeader1);

        RaHeader raHeader2 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader2);
        raHeader2.setPaymentDate(paymentDate2);
        raHeaderDao.persist(raHeader2);

        RaHeader raHeader3 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader3);
        raHeader3.setPaymentDate(paymentDate3);
        raHeaderDao.persist(raHeader3);

        RaHeader raHeader4 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader4);
        raHeader4.setPaymentDate(paymentDate4);
        raHeaderDao.persist(raHeader4);

        RaHeader raHeader5 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader5);
        raHeader5.setPaymentDate(paymentDate5);
        raHeaderDao.persist(raHeader5);

        RaHeader raHeader6 = new RaHeader();
        EntityDataGenerator.generateTestDataForModelClass(raHeader6);
        raHeader6.setPaymentDate(paymentDate4);
        raHeaderDao.persist(raHeader6);

        int billingNo1 = 101;
        int billingNo2 = 202;

        Date startDate = new Date(dfm.parse("20090101").getTime());
        Date endDate = new Date(dfm.parse("20121215").getTime());
        Locale locale = Locale.getDefault();

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setRaHeaderNo(raHeader1.getId());
        raDetail1.setBillingNo(billingNo1);
        raDetail1.setProviderOhipNo(provider1.getOhipNo());
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setRaHeaderNo(raHeader2.getId());
        raDetail2.setBillingNo(billingNo2);
        raDetail2.setProviderOhipNo(provider1.getOhipNo());
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setRaHeaderNo(raHeader3.getId());
        raDetail3.setBillingNo(billingNo1);
        raDetail3.setProviderOhipNo(provider1.getOhipNo());
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setRaHeaderNo(raHeader4.getId());
        raDetail4.setBillingNo(billingNo1);
        raDetail4.setProviderOhipNo(provider1.getOhipNo());
        dao.persist(raDetail4);

        RaDetail raDetail5 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail5);
        raDetail5.setRaHeaderNo(raHeader5.getId());
        raDetail5.setBillingNo(billingNo1);
        raDetail5.setProviderOhipNo(provider1.getOhipNo());
        dao.persist(raDetail5);

        RaDetail raDetail6 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail6);
        raDetail6.setRaHeaderNo(raHeader6.getId());
        raDetail6.setBillingNo(billingNo1);
        raDetail6.setProviderOhipNo(provider2.getOhipNo());
        dao.persist(raDetail6);

        hibernateTemplate.flush();

        List<RaDetail> expectedResult = Arrays.asList(raDetail2, raDetail3, raDetail4);
        List<RaDetail> result = dao.getRaDetailByDate(provider1, startDate, endDate, locale);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- getRaDetailByClaimNo test ---

    @Test
    @Tag("read")
    @DisplayName("should return RaDetails when finding by claimNo")
    void shouldReturnRaDetails_byClaimNo() throws Exception {
        String claimNo1 = "111";
        String claimNo2 = "222";

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setClaimNo(claimNo1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setClaimNo(claimNo2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setClaimNo(claimNo1);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setClaimNo(claimNo1);
        dao.persist(raDetail4);

        hibernateTemplate.flush();

        List<RaDetail> expectedResult = Arrays.asList(raDetail1, raDetail3, raDetail4);
        List<RaDetail> result = dao.getRaDetailByClaimNo(claimNo1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- getBillingExplanatoryList test ---

    @Test
    @Tag("read")
    @DisplayName("should return distinct error codes for a billing number")
    void shouldReturnDistinctErrorCodes_byBillingNo() throws Exception {
        int billingNo1 = 111;
        int billingNo2 = 222;

        String errorCode1 = "a";
        String errorCode2 = "b";
        String errorCode4 = "d";

        RaDetail raDetail1 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail1);
        raDetail1.setBillingNo(billingNo1);
        raDetail1.setErrorCode(errorCode1);
        dao.persist(raDetail1);

        RaDetail raDetail2 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail2);
        raDetail2.setBillingNo(billingNo2);
        raDetail2.setErrorCode(errorCode2);
        dao.persist(raDetail2);

        RaDetail raDetail3 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail3);
        raDetail3.setBillingNo(billingNo1);
        raDetail3.setErrorCode(errorCode1);
        dao.persist(raDetail3);

        RaDetail raDetail4 = new RaDetail();
        EntityDataGenerator.generateTestDataForModelClass(raDetail4);
        raDetail4.setBillingNo(billingNo2);
        raDetail4.setErrorCode(errorCode4);
        dao.persist(raDetail4);

        hibernateTemplate.flush();

        List<String> expectedResult = Arrays.asList(errorCode1);
        List<String> result = dao.getBillingExplanatoryList(billingNo1);

        assertThat(result).hasSize(expectedResult.size());
        for (int i = 0; i < expectedResult.size(); i++) {
            assertThat(result.get(i)).isEqualTo(expectedResult.get(i));
        }
    }

    // --- simple query method tests ---

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by billingNo, serviceDate, and providerNo")
    void shouldReturnNonNullResult_byBillingNoServiceDateAndProviderNo() {
        List<RaDetail> result = dao.findByBillingNoServiceDateAndProviderNo(100, ConversionUtils.toDateString(new Date()), "100");
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by billingNo and errorCode")
    void shouldReturnNonNullResult_byBillingNoAndErrorCode() {
        List<RaDetail> result = dao.findByBillingNoAndErrorCode(100, "CODE");
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by header and billing numbers")
    void shouldReturnNonNullResult_byHeaderAndBillingNos() {
        List<RaDetail> result = dao.findByHeaderAndBillingNos(100, 100);
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by raHeaderNo and service codes")
    void shouldReturnNonNullResult_byRaHeaderNoAndServiceCodes() {
        List<RaDetail> result = dao.findByRaHeaderNoAndServiceCodes(100, Arrays.asList("CODE"));
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by raHeaderNo and providerOhipNo")
    void shouldReturnNonNullResult_byRaHeaderNoAndProviderOhipNo() {
        List<RaDetail> result = dao.findByRaHeaderNoAndProviderOhipNo(100, "10");
        assertThat(result).isNotNull();
    }
}
