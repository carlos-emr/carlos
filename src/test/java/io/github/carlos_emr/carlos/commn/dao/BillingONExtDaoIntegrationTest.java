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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link BillingONExtDao} covering full method coverage
 * matching the legacy {@code BillingONExtDaoTest}.
 *
 * <p>Tests cover getPayment, getRefund, getRemitTo, getBillTo, getBillToInactive,
 * and find operations.</p>
 *
 * @since 2026-03-07
 * @see BillingONExtDao
 */
@DisplayName("BillingONExt Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing")
@Transactional
public class BillingONExtDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONExtDao dao;

    @Autowired
    private BillingONPaymentDao paymentDao;

    @Autowired
    private BillingONCHeader1Dao cHeader1Dao;

    /** Parent header record satisfying the FK constraint on BillingONPayment.billingNo. */
    private BillingONCHeader1 parentHeader;

    @BeforeEach
    void createParentHeader() {
        parentHeader = new BillingONCHeader1();
        parentHeader.setHeaderId(0);
        parentHeader.setDemographicNo(1);
        parentHeader.setProviderNo("111111");
        parentHeader.setStatus("O");
        cHeader1Dao.persist(parentHeader);
        hibernateTemplate.flush();
    }

    // --- getPayment tests ---

    @Test
    @Tag("read")
    @DisplayName("should return valid payment amount when matching ext record exists")
    void shouldReturnPayment_whenValidDataProvided() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("payment");
        extraBillingPayment.setValue("10");
        extraBillingPayment.setPaymentId(paymentRecord.getId());

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal payment = dao.getPayment(paymentRecord);
        assertThat(payment).isEqualTo(new BigDecimal("10"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return zero when no matching payment ext records exist")
    void shouldReturnZeroPayment_whenNoMatchingRecordsExist() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setPaymentId(paymentRecord.getId());
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("notpayment");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal payment = dao.getPayment(paymentRecord);
        assertThat(payment).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return zero when payment value is not a valid number")
    void shouldReturnZeroPayment_whenValueIsInvalid() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("payment");
        extraBillingPayment.setValue("abc123");
        extraBillingPayment.setPaymentId(paymentRecord.getId());

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal payment = dao.getPayment(paymentRecord);
        assertThat(payment).isEqualTo(new BigDecimal("0.00"));
    }

    // --- getRefund tests ---

    @Test
    @Tag("read")
    @DisplayName("should return valid refund amount when matching ext record exists")
    void shouldReturnRefund_whenValidDataProvided() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("refund");
        extraBillingPayment.setValue("10");
        extraBillingPayment.setPaymentId(paymentRecord.getId());

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal refund = dao.getRefund(paymentRecord);
        assertThat(refund).isEqualTo(new BigDecimal("10"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return zero refund when no matching refund ext records exist")
    void shouldReturnZeroRefund_whenNoMatchingRecordsExist() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setPaymentId(paymentRecord.getId());
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("notpayment");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal refund = dao.getRefund(paymentRecord);
        assertThat(refund).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return zero refund when refund value is not a valid number")
    void shouldReturnZeroRefund_whenValueIsInvalid() throws Exception {
        BillingONPayment paymentRecord = new BillingONPayment();
        paymentRecord.setBillingNo(parentHeader.getId());
        paymentRecord.setPaymentDate(new Date());
        paymentDao.persist(paymentRecord);
        hibernateTemplate.flush();

        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setKeyVal("refund");
        extraBillingPayment.setValue("abc123");
        extraBillingPayment.setPaymentId(paymentRecord.getId());

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BigDecimal refund = dao.getRefund(paymentRecord);
        assertThat(refund).isEqualTo(new BigDecimal("0.00"));
    }

    // --- getRemitTo tests ---

    @Test
    @Tag("read")
    @DisplayName("should return remitTo ext record when matching active record exists")
    void shouldReturnRemitTo_whenValidDataProvided() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('1');
        extraBillingPayment.setKeyVal("remitTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getRemitTo(parentHeader);
        assertThat(billingRecord).isEqualTo(extraBillingPayment);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null remitTo when status does not match active")
    void shouldReturnNullRemitTo_whenStatusIsNotActive() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('A');
        extraBillingPayment.setKeyVal("remitTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getRemitTo(parentHeader);
        assertThat(billingRecord).isNull();
    }

    // --- getBillTo tests ---

    @Test
    @Tag("read")
    @DisplayName("should return billTo ext record when matching active record exists")
    void shouldReturnBillTo_whenValidDataProvided() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('1');
        extraBillingPayment.setKeyVal("billTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getBillTo(parentHeader);
        assertThat(billingRecord).isEqualTo(extraBillingPayment);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null billTo when status does not match active")
    void shouldReturnNullBillTo_whenStatusIsNotActive() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('A');
        extraBillingPayment.setKeyVal("billTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getBillTo(parentHeader);
        assertThat(billingRecord).isNull();
    }

    // --- getBillToInactive tests ---

    @Test
    @Tag("read")
    @DisplayName("should return inactive billTo ext record when matching inactive record exists")
    void shouldReturnBillToInactive_whenValidDataProvided() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('0');
        extraBillingPayment.setKeyVal("billTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getBillToInactive(parentHeader);
        assertThat(billingRecord).isEqualTo(extraBillingPayment);
    }

    @Test
    @Tag("read")
    @DisplayName("should return null billToInactive when status does not match inactive")
    void shouldReturnNullBillToInactive_whenStatusIsNotInactive() throws Exception {
        BillingONExt extraBillingPayment = new BillingONExt();
        extraBillingPayment.setBillingNo(parentHeader.getId());
        extraBillingPayment.setStatus('A');
        extraBillingPayment.setKeyVal("billTo");

        dao.persist(extraBillingPayment);
        hibernateTemplate.flush();

        BillingONExt billingRecord = dao.getBillToInactive(parentHeader);
        assertThat(billingRecord).isNull();
    }

    // --- find tests ---

    @Test
    @Tag("read")
    @DisplayName("should return non-null result when finding by billingNo, key, and date range")
    void shouldReturnNonNullResult_whenFindingByBillingNoKeyAndDateRange() throws Exception {
        List<BillingONExt> result = dao.find(100, "KEY", new Date(), new Date());
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
