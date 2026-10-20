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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link BillingCorrectionService} exercised through
 * {@link CarlosTestBase}'s real Spring context + H2 database.
 *
 * <p>Where the {@code BillingCorrectionServiceValidationThrowsUnitTest}
 * verifies the validation throws fast against mocked DAOs, this test
 * exercises the happy-path persistence chain end-to-end so a future
 * refactor that drifts the cross-DAO interaction (e.g. forgets to flush
 * the Hibernate session before the JPA reads) fails loudly. Closes the
 * Phase&nbsp;1 plan slot for "1 transactional integration test for
 * BillingCorrectionService" that was carved out when the round originally
 * shipped.</p>
 *
 * <p>The production service is transactional; this test instantiates it directly
 * with autowired DAO collaborators — but the surrounding test context
 * provides JPA + standalone Hibernate session + transactional rollback,
 * which is what makes the "transactional" qualifier in the original plan
 * meaningful. Each test runs inside the {@link Transactional} envelope
 * inherited from the base class and rolls back at the end, so cross-test
 * isolation is preserved without manual tear-down.</p>
 *
 * @since 2026-04-26
 */
@DisplayName("BillingCorrectionService integration")
@Tag("integration")
@Tag("billing")
@Transactional
public class BillingCorrectionServiceIntegrationTest extends CarlosTestBase {

    // Real DAOs — addThirdPartyPayment exercises these end-to-end.
    @Autowired
    private BillingONCHeader1Dao bCh1Dao;
    @Autowired
    private BillingONPaymentDao bPaymentDao;
    @Autowired
    private BillingPaymentTypeDao billingPaymentTypeDao;
    @Autowired
    private BillingONExtDao billExtDao;

    // Unused by addThirdPartyPayment — only updateInvoice touches these.
    // Mocked rather than autowired so this test doesn't need to register
    // the @Service-annotated BillingONService in the test context (which
    // only auto-scans @Repository beans).
    private BillingONRepoDao billRepoDao;
    private ProviderDao providerDao;
    private BillingServiceDao billingServiceDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private BillingCorrectionService service;
    private MockHttpServletRequest request;
    private LoggedInInfo loggedInInfo;

    private BillingONCHeader1 persistedHeader;
    private BillingPaymentType persistedPaymentType;

    @BeforeEach
    void setUp() {
        billRepoDao = Mockito.mock(BillingONRepoDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);

        service = new BillingCorrectionService(
                bPaymentDao, bCh1Dao, billExtDao, billingPaymentTypeDao,
                billRepoDao, providerDao, billingServiceDao);

        request = new MockHttpServletRequest();
        loggedInInfo = Mockito.mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        persistedPaymentType = createAndPersistPaymentType("CASH-IT");
        persistedHeader = createAndPersistHeader(100, "999998");
    }

    /**
     * Happy path: a valid bill + valid form parameters drives the service to
     * persist a {@link BillingONPayment} and updates the header's running
     * paid amount. End-to-end check that the {@code bCh1Dao.find} →
     * {@code billingPaymentTypeDao.find} → {@code bPaymentDao.createPayment}
     * chain works against the real persistence context.
     */
    @Test
    @DisplayName("should persist payment and update bill paid total when params valid")
    void shouldPersistPaymentAndUpdateBillPaidTotal_whenParamsValid() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("amtPaid", "25.00");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");

        String result = service.addThirdPartyPayment(loggedInInfo, request);

        assertThat(result).isEqualTo("success");

        // BillingONCHeader1Dao / BillingONPaymentDao both extend AbstractDaoImpl
        // which uses the JPA EntityManager — flush() pushes pending writes
        // (the new payment row + the bCh1.paid mutation) to the DB; clear()
        // forces the next read to round-trip rather than serve a stale L1
        // entity.
        entityManager.flush();
        entityManager.clear();

        List<BillingONPayment> payments = entityManager
                .createQuery("FROM BillingONPayment p WHERE p.billingNo = :id", BillingONPayment.class)
                .setParameter("id", persistedHeader.getId())
                .getResultList();
        assertThat(payments)
                .as("addThirdPartyPayment should persist exactly one BillingONPayment row")
                .hasSize(1);

        // Read via the Hibernate path used by the service so we observe the
        // post-merge state. JPA EntityManager.find() pulls from a separate
        // L1 cache that may not see the Hibernate-side write.
        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        assertThat(reloaded.getPaid())
                .as("bCh1.paid should be incremented by the payment amount on a P-type")
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    /**
     * The validator must throw {@link BillingValidationException} BEFORE any
     * DAO write happens — a bad amount must not leave a half-persisted
     * payment behind. Verified end-to-end against H2: no
     * {@link BillingONPayment} row may exist for this header after the
     * exception bubbles.
     */
    @Test
    @DisplayName("should not persist payment when amtPaid is non-numeric")
    void shouldNotPersistPayment_whenAmtPaidNonNumeric() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("amtPaid", "not-a-number");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not a valid number");

        hibernateTemplate.flush();
        entityManager.clear();

        List<BillingONPayment> payments = entityManager
                .createQuery("FROM BillingONPayment p WHERE p.billingNo = :id", BillingONPayment.class)
                .setParameter("id", persistedHeader.getId())
                .getResultList();
        assertThat(payments)
                .as("rejected payment must not leave any BillingONPayment row behind")
                .isEmpty();
    }

    /**
     * Refund path ({@code payType="R"}). The {@code createPayment} contract
     * subtracts the amount from the bill's running {@code paid} total
     * (R-type is the negative of P-type) and records the value in a
     * {@code BillingONExt} row keyed {@code "refund"} on the new payment.
     */
    @Test
    @DisplayName("should persist refund payment and decrement bill paid total")
    void shouldPersistRefundPaymentAndDecrementBillPaidTotal_whenPayTypeIsRefund() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("amtPaid", "10.00");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "R");

        String result = service.addThirdPartyPayment(loggedInInfo, request);

        assertThat(result).isEqualTo("success");
        entityManager.flush();
        entityManager.clear();

        List<BillingONPayment> payments = entityManager
                .createQuery("FROM BillingONPayment p WHERE p.billingNo = :id", BillingONPayment.class)
                .setParameter("id", persistedHeader.getId())
                .getResultList();
        assertThat(payments).hasSize(1);

        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        assertThat(reloaded.getPaid())
                .as("R-type payment subtracts from the running paid total: 0.00 - 10.00 = -10.00")
                .isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("should reject when billing_no is missing")
    void shouldThrowAndNotPersist_whenBillingNoIsMissing() {
        request.setParameter("amtPaid", "25.00");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");
        // billing_no parameter intentionally absent

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invalid billing_no");

        assertNoPaymentRowsForHeader();
    }

    @Test
    @DisplayName("should reject when billing_no resolves to no row (404)")
    void shouldThrowAndNotPersist_whenBillNotFound() {
        request.setParameter("billing_no", "99999999");  // no header exists with this id
        request.setParameter("amtPaid", "25.00");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("not found");

        assertNoPaymentRowsForHeader();
    }

    @Test
    @DisplayName("should reject when amtPaid is missing")
    void shouldThrowAndNotPersist_whenAmtPaidMissing() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");
        // amtPaid intentionally absent — covers the null branch separately
        // from the "amount is non-numeric" branch in the existing test.

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("amount is missing");

        assertNoPaymentRowsForHeader();
    }

    @Test
    @DisplayName("should reject when payMethod is not configured")
    void shouldThrowAndNotPersist_whenPayMethodInvalid() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("amtPaid", "25.00");
        request.setParameter("payMethod", "99999");  // no BillingPaymentType row with this id
        request.setParameter("payType", "P");

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("pay-method");

        assertNoPaymentRowsForHeader();
    }

    @Test
    @DisplayName("should reject when payType is neither P nor R")
    void shouldThrowAndNotPersist_whenPayTypeInvalid() {
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("amtPaid", "25.00");
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "X");  // not P or R

        assertThatThrownBy(() -> service.addThirdPartyPayment(loggedInInfo, request))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("pay-type");

        assertNoPaymentRowsForHeader();
    }

    @Test
    @DisplayName("should aggregate two separate payments against the same bill")
    void shouldAggregatePaidTotal_acrossTwoSequentialPayments() {
        // Sequential add of two P-type payments — bill.paid should reflect
        // the running sum, and two BillingONPayment rows should exist.
        request.setParameter("billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("payMethod", String.valueOf(persistedPaymentType.getId()));
        request.setParameter("payType", "P");

        request.setParameter("amtPaid", "15.00");
        service.addThirdPartyPayment(loggedInInfo, request);
        request.setParameter("amtPaid", "10.00");
        service.addThirdPartyPayment(loggedInInfo, request);

        entityManager.flush();
        entityManager.clear();

        List<BillingONPayment> payments = entityManager
                .createQuery("FROM BillingONPayment p WHERE p.billingNo = :id", BillingONPayment.class)
                .setParameter("id", persistedHeader.getId())
                .getResultList();
        assertThat(payments).hasSize(2);

        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        assertThat(reloaded.getPaid()).isEqualByComparingTo("25.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HCP", "RMB", "WCB"})
    void shouldPersistSettlementAndPaidBalance_whenMinistryInvoiceIsSettled(String payProgram) {
        persistedHeader.setPayProgram(payProgram);
        persistedHeader.setPaid(new BigDecimal("12.50"));
        prepareCorrection(payProgram);
        Date before = new Date();

        assertThat(service.updateInvoice(loggedInInfo, request)).isEqualTo("submitClose");
        entityManager.flush();
        entityManager.clear();

        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        List<BillingONPayment> payments = bPaymentDao.listPaymentsByBillingNo(reloaded.getId());
        assertThat(payments).hasSize(1);
        BillingONPayment payment = payments.getFirst();
        assertThat(payment.getBillingNo()).isEqualTo(reloaded.getId());
        assertThat(payment.getBillingONCheader1().getId()).isEqualTo(reloaded.getId());
        assertThat(payment.getTotal_payment()).isEqualByComparingTo("37.50");
        assertThat(payment.getCreator()).isEqualTo("999998");
        assertThat(payment.getPaymentTypeId()).isZero();
        assertThat(payment.getPaymentDate()).isAfterOrEqualTo(before).isBeforeOrEqualTo(new Date());
        assertThat(reloaded.getPaid()).isEqualByComparingTo("50.00");
        assertThat(reloaded.getStatus()).isEqualTo("S");
        assertThat(billExtDao.getAccountVal(reloaded.getId(), BillingONExtDao.KEY_PAYMENT))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void shouldNotDuplicateSettlement_whenSavedAgainOrReopenedAndResettled() {
        prepareCorrection("HCP");
        service.updateInvoice(loggedInInfo, request);
        service.updateInvoice(loggedInInfo, request);
        // Reopening an already paid invoice must not collect its amount again.
        request.setParameter("status", "O");
        service.updateInvoice(loggedInInfo, request);
        request.setParameter("status", "S");
        service.updateInvoice(loggedInInfo, request);
        entityManager.flush();
        entityManager.clear();

        assertThat(bPaymentDao.listPaymentsByBillingNo(persistedHeader.getId())).hasSize(1);
        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        assertThat(reloaded.getPaid()).isEqualByComparingTo("50.00");
        assertThat(reloaded.getStatus()).isEqualTo("S");
    }

    @Test
    void shouldUseCorrectedItemTotal_whenSettlingDuringFeeEdit() {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(persistedHeader.getId());
        item.setServiceCode("A007A");
        item.setServiceCount("1");
        item.setFee("50.00");
        item.setDx("250");
        item.setServiceDate(java.sql.Date.valueOf("2026-03-04"));
        item.setStatus("O");
        persistedHeader.addBillingItem(item);
        entityManager.flush();
        entityManager.clear();
        prepareCorrection("HCP");
        request.setParameter("xml_diagnostic_detail", "250");
        request.setParameter("servicecode0", "A007A");
        request.setParameter("billingunit0", "1");
        request.setParameter("billingamount0", "75.00");
        request.setParameter("itemStatus0", "S");

        service.updateInvoice(loggedInInfo, request);
        entityManager.flush();
        entityManager.clear();

        BillingONCHeader1 reloaded = bCh1Dao.find(persistedHeader.getId());
        assertThat(reloaded.getTotal()).isEqualByComparingTo("75.00");
        assertThat(reloaded.getPaid()).isEqualByComparingTo("75.00");
        assertThat(bPaymentDao.listPaymentsByBillingNo(reloaded.getId())).singleElement()
                .satisfies(payment -> assertThat(payment.getTotal_payment()).isEqualByComparingTo("75.00"));
    }

    private void prepareCorrection(String payProgram) {
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setOhipNo("123456");
        provider.setRmaNo("0001");
        when(providerDao.getProvider("999998")).thenReturn(provider);
        request.setPreferredLocales(List.of(Locale.CANADA));
        request.setParameter("xml_billing_no", String.valueOf(persistedHeader.getId()));
        request.setParameter("status", "S");
        request.setParameter("oldStatus", "O");
        request.setParameter("payProgram", payProgram);
        request.setParameter("xml_appointment_date", "2026-03-04");
        request.setParameter("xml_vdate", "");
        request.setParameter("rdohip", "");
        request.setParameter("visittype", "00");
        request.setParameter("clinic_ref_code", "0001");
        request.setParameter("provider_no", "999998");
        request.setParameter("comment", "");
        request.setParameter("site", "");
        request.setParameter("hc_type", "ON");
        request.setParameter("xml_slicode", "P1");
        request.setParameter("submit", "Save");
    }

    private void assertNoPaymentRowsForHeader() {
        hibernateTemplate.flush();
        entityManager.clear();
        List<BillingONPayment> payments = entityManager
                .createQuery("FROM BillingONPayment p WHERE p.billingNo = :id", BillingONPayment.class)
                .setParameter("id", persistedHeader.getId())
                .getResultList();
        assertThat(payments)
                .as("rejected payment must not leave any BillingONPayment row behind")
                .isEmpty();
    }

    private BillingONCHeader1 createAndPersistHeader(int demoNo, String providerNo) {
        BillingONCHeader1 h = new BillingONCHeader1();
        h.setHeaderId(0);
        h.setDemographicNo(demoNo);
        h.setProviderNo(providerNo);
        h.setStatus("O");
        h.setBillingDate(new Date());
        h.setBillingTime(new Date());
        h.setPayProgram("HCP");
        h.setVisitType("00");
        h.setTotal(new BigDecimal("50.00"));
        h.setPaid(new BigDecimal("0.00"));
        h.setApptProviderNo(providerNo);
        h.setCreator(providerNo);
        h.setAppointmentNo(0);
        h.setFaciltyNum("0001");
        entityManager.persist(h);
        entityManager.flush();
        return h;
    }

    private BillingPaymentType createAndPersistPaymentType(String name) {
        BillingPaymentType pt = new BillingPaymentType();
        pt.setPaymentType(name);
        entityManager.persist(pt);
        entityManager.flush();
        return pt;
    }
}
