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
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.parallel.ResourceLock;
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
@Tag("billing")
@Transactional
public class BillingONPaymentDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONPaymentDao dao;

    @Autowired
    private BillingONCHeader1Dao daoBONCH;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("read")
    @DisplayName("should return 3rd party pay records sorted by payment date")
    void shouldReturnPayRecordsSortedByDate_whenSearchingByBill() throws Exception {
        BillingONCHeader1 bONCHeader1 = new BillingONCHeader1();
        bONCHeader1.setHeaderId(0);
        bONCHeader1.setDemographicNo(1);
        bONCHeader1.setProviderNo("111111");
        bONCHeader1.setStatus("O");
        daoBONCH.persist(bONCHeader1);
        hibernateTemplate.flush();

        int billingNo = bONCHeader1.getId();

        BillingONPayment bONPayment1 = new BillingONPayment();
        Date date1 = new Date(dfm.parse("20110101").getTime());
        bONPayment1.setBillingNo(billingNo);
        bONPayment1.setPaymentDate(date1);

        BillingONPayment bONPayment2 = new BillingONPayment();
        Date date2 = new Date(dfm.parse("20110701").getTime());
        bONPayment2.setBillingNo(billingNo);
        bONPayment2.setPaymentDate(date2);

        BillingONPayment bONPayment3 = new BillingONPayment();
        Date date3 = new Date(dfm.parse("20110301").getTime());
        bONPayment3.setBillingNo(billingNo);
        bONPayment3.setPaymentDate(date3);

        dao.persist(bONPayment1);
        dao.persist(bONPayment2);
        dao.persist(bONPayment3);
        hibernateTemplate.flush();

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
        bONCHeader1.setHeaderId(0);
        bONCHeader1.setDemographicNo(1);
        bONCHeader1.setProviderNo("111111");
        bONCHeader1.setStatus("O");
        daoBONCH.persist(bONCHeader1);
        hibernateTemplate.flush();

        int billingNo = bONCHeader1.getId();
        Date startDate = new Date(dfm.parse("20101230").getTime());
        Date endDate = new Date(dfm.parse("20120101").getTime());

        BillingONPayment bONPayment1 = new BillingONPayment();
        Date date1 = new Date(dfm.parse("20110102").getTime());
        bONPayment1.setBillingNo(billingNo);
        bONPayment1.setPaymentDate(date1);

        BillingONPayment bONPayment2 = new BillingONPayment();
        Date date2 = new Date(dfm.parse("20110302").getTime());
        bONPayment2.setBillingNo(billingNo);
        bONPayment2.setPaymentDate(date2);

        BillingONPayment bONPayment3 = new BillingONPayment();
        Date date3 = new Date(dfm.parse("20110502").getTime());
        bONPayment3.setBillingNo(billingNo);
        bONPayment3.setPaymentDate(date3);

        dao.persist(bONPayment1);
        dao.persist(bONPayment2);
        dao.persist(bONPayment3);
        hibernateTemplate.flush();

        List<BillingONPayment> result = dao.find3rdPartyPayRecordsByBill(bONCHeader1, startDate, endDate);
        List<BillingONPayment> expectedResult = Arrays.asList(
                bONPayment1, bONPayment2, bONPayment3);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }

    @Test
    @Tag("read")
    @DisplayName("findWithExtItems should populate billingONExtItems collection in one query")
    void shouldPopulateExtItems_inOneQueryWhenFindWithExtItems() throws Exception {
        BillingONCHeader1 bONCHeader1 = new BillingONCHeader1();
        bONCHeader1.setHeaderId(0);
        bONCHeader1.setDemographicNo(1);
        bONCHeader1.setProviderNo("111111");
        bONCHeader1.setStatus(BillingONCHeader1.OPEN);
        daoBONCH.persist(bONCHeader1);
        hibernateTemplate.flush();

        BillingONPayment payment = new BillingONPayment();
        payment.setBillingNo(bONCHeader1.getId());
        payment.setPaymentDate(new Date(dfm.parse("20260101").getTime()));

        BillingONExt ext1 = new BillingONExt();
        ext1.setBillingNo(bONCHeader1.getId());
        ext1.setKeyVal("payment");
        ext1.setValue("100.00");
        BillingONExt ext2 = new BillingONExt();
        ext2.setBillingNo(bONCHeader1.getId());
        ext2.setKeyVal("refund");
        ext2.setValue("0.00");

        // CascadeType.ALL on BillingONPayment.billingONExtItems persists the
        // children alongside the payment.
        payment.getBillingONExtItems().add(ext1);
        payment.getBillingONExtItems().add(ext2);
        dao.persist(payment);
        hibernateTemplate.flush();

        // Detach so a subsequent .find() would otherwise lazy-init.
        entityManager.clear();

        BillingONPayment loaded = dao.findWithExtItems(payment.getId());

        assertThat(loaded).isNotNull();
        // The collection is populated by the LEFT JOIN FETCH; iterating it
        // outside the session must NOT throw LazyInitializationException.
        assertThat(loaded.getBillingONExtItems()).hasSize(2);
        assertThat(loaded.getBillingONExtItems())
                .extracting(BillingONExt::getKeyVal)
                .containsExactlyInAnyOrder("payment", "refund");
    }

    @Test
    @Tag("read")
    @DisplayName("findWithExtItems should return null when paymentId is null")
    void shouldReturnNull_whenPaymentIdIsNullOnFindWithExtItems() {
        assertThat(dao.findWithExtItems(null)).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("find3rdPartyPaymentsByBillingNoWithExtItems should populate ext items")
    void shouldReturnPaymentsWithExtItems_whenFind3rdPartyPaymentsByBillingNoWithExtItems() throws Exception {
        BillingONCHeader1 bONCHeader1 = new BillingONCHeader1();
        bONCHeader1.setHeaderId(0);
        bONCHeader1.setDemographicNo(1);
        bONCHeader1.setProviderNo("111111");
        bONCHeader1.setStatus(BillingONCHeader1.OPEN);
        daoBONCH.persist(bONCHeader1);
        hibernateTemplate.flush();

        int billingNo = bONCHeader1.getId();

        BillingONPayment payment1 = new BillingONPayment();
        payment1.setBillingNo(billingNo);
        payment1.setPaymentDate(new Date(dfm.parse("20260101").getTime()));
        BillingONExt ext = new BillingONExt();
        ext.setBillingNo(billingNo);
        ext.setKeyVal("payment");
        ext.setValue("50.00");
        payment1.getBillingONExtItems().add(ext);

        BillingONPayment payment2 = new BillingONPayment();
        payment2.setBillingNo(billingNo);
        payment2.setPaymentDate(new Date(dfm.parse("20260201").getTime()));

        dao.persist(payment1);
        dao.persist(payment2);
        hibernateTemplate.flush();

        entityManager.clear();

        List<BillingONPayment> result = dao.find3rdPartyPaymentsByBillingNoWithExtItems(billingNo);

        // DISTINCT keeps the parent count correct despite the cartesian
        // product from LEFT JOIN FETCH on payment1's single ext row.
        assertThat(result).hasSize(2);
        int totalExt = result.stream().mapToInt(p -> p.getBillingONExtItems().size()).sum();
        assertThat(totalExt).isEqualTo(1);
    }

    @Test
    @Tag("read")
    @DisplayName("getTotalSumByBillingNoWeb should return zero when billingNo is non-numeric")
    void shouldReturnZeroAsCurrency_whenBillingNoNonNumericInTotalSum() {
        // The narrowed catch must observe and convert NFE to a zero render
        // path; a future refactor that swaps NumberFormatException for a
        // broader Exception (or removes the catch) would surface here.
        String result = dao.getTotalSumByBillingNoWeb("not-a-number");
        assertThat(result).isNotNull();
        assertThat(result).contains("0.00");
    }

    @Test
    @Tag("read")
    @DisplayName("getPaymentsRefundByBillingNoWeb should return zero when billingNo is non-numeric")
    void shouldReturnZeroAsCurrency_whenBillingNoNonNumericInRefund() {
        String result = dao.getPaymentsRefundByBillingNoWeb("not-a-number");
        assertThat(result).isNotNull();
        assertThat(result).contains("0.00");
    }

    @ParameterizedTest
    @CsvSource({"2026-03-08", "2026-11-01", "2028-02-29", "2026-12-31"})
    @ResourceLock("java.util.TimeZone.default")
    void shouldIncludeWholeEndDateAndExcludeAdjacentDays_whenQueryingSingleAndMultipleBills(String day) {
        var originalZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
        try {
            var selected = paymentReportHeader();
            var other = paymentReportHeader();
            var start = java.sql.Timestamp.valueOf(day + " 00:00:00");
            var endOfDay = java.sql.Timestamp.valueOf(day + " 23:59:59.999");
            var nextDay = java.sql.Timestamp.valueOf(java.time.LocalDate.parse(day).plusDays(1).atStartOfDay());
            paymentReportRow(selected, new Date(start.getTime() - 1));
            var first = paymentReportRow(selected, start);
            var last = paymentReportRow(selected, endOfDay);
            paymentReportRow(selected, nextDay);
            var otherPayment = paymentReportRow(other, start);
            hibernateTemplate.flush();
            entityManager.clear();

            assertThat(dao.find3rdPartyPayRecordsByBill(selected, start, start))
                    .extracting(BillingONPayment::getId).containsExactly(first.getId(), last.getId());
            assertThat(dao.find3rdPartyPayRecordsByBills(List.of(selected.getId()), start, start))
                    .extracting(BillingONPayment::getId).containsExactly(first.getId(), last.getId());
            assertThat(dao.find3rdPartyPayRecordsByBills(List.of(selected.getId(), other.getId()), start, start))
                    .extracting(BillingONPayment::getId)
                    .containsExactlyInAnyOrder(first.getId(), last.getId(), otherPayment.getId());
            assertThat(start).isEqualTo(java.sql.Timestamp.valueOf(day + " 00:00:00"));
        } finally {
            java.util.TimeZone.setDefault(originalZone);
        }
    }

    @ParameterizedTest
    @CsvSource({"2026-03-08", "2026-11-01", "2028-02-29", "2026-12-31"})
    @ResourceLock("java.util.TimeZone.default")
    void shouldSelectInvoicesWithLatePayments_whenFilteringByProviderOrAllProviders(String day) {
        var originalZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
        try {
            var selected = paymentReportHeader();
            var otherProvider = paymentReportHeader("222222");
            var before = paymentReportHeader();
            var after = paymentReportHeader();
            var start = java.sql.Timestamp.valueOf(day + " 00:00:00");
            paymentReportRow(selected, java.sql.Timestamp.valueOf(day + " 12:34:56"));
            paymentReportRow(selected, java.sql.Timestamp.valueOf(day + " 23:59:59.999"));
            paymentReportRow(otherProvider, java.sql.Timestamp.valueOf(day + " 15:00:00"));
            paymentReportRow(before, new Date(start.getTime() - 1));
            paymentReportRow(after, java.sql.Timestamp.valueOf(java.time.LocalDate.parse(day).plusDays(1).atStartOfDay()));
            hibernateTemplate.flush();
            entityManager.clear();
            var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
            provider.setProviderNo("111111");

            assertThat(daoBONCH.get3rdPartyInvoiceByProvider(provider, start, start, java.util.Locale.CANADA))
                    .extracting(BillingONCHeader1::getId).containsExactly(selected.getId());
            assertThat(daoBONCH.get3rdPartyInvoiceByDate(start, start, java.util.Locale.CANADA))
                    .extracting(BillingONCHeader1::getId).containsExactlyInAnyOrder(selected.getId(), otherProvider.getId());
        } finally {
            java.util.TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void shouldReturnNoPayments_whenInvoiceSelectionIsEmpty() {
        assertThat(dao.find3rdPartyPayRecordsByBills(List.of(), null, null)).isEmpty();
        assertThat(dao.find3rdPartyPayRecordsByBills(null, null, null)).isEmpty();
    }

    private BillingONCHeader1 paymentReportHeader() {
        return paymentReportHeader("111111");
    }

    private BillingONCHeader1 paymentReportHeader(String providerNo) {
        var header = new BillingONCHeader1();
        header.setHeaderId(0);
        header.setDemographicNo(1);
        header.setProviderNo(providerNo);
        header.setStatus("S");
        daoBONCH.persist(header);
        hibernateTemplate.flush();
        return header;
    }

    private BillingONPayment paymentReportRow(BillingONCHeader1 header, Date date) {
        var payment = new BillingONPayment();
        payment.setBillingNo(header.getId());
        payment.setPaymentDate(date);
        payment.setTotal_payment(new java.math.BigDecimal("12.50"));
        dao.persist(payment);
        return payment;
    }

}
