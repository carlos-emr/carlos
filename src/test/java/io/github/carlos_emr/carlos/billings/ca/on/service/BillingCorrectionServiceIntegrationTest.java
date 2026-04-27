/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONInvoiceTotalsCalculator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * <p>The service itself isn't a Spring bean — it's instantiated directly
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
    private BillingONInvoiceTotalsCalculator totalsCalculator;
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
        totalsCalculator = Mockito.mock(BillingONInvoiceTotalsCalculator.class);
        billRepoDao = Mockito.mock(BillingONRepoDao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);

        service = new BillingCorrectionService(
                bPaymentDao, bCh1Dao, billExtDao, billingPaymentTypeDao,
                totalsCalculator, billRepoDao, providerDao, billingServiceDao);

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
    void shouldPersistPayment_andUpdateBillPaidTotal_whenParamsValid() {
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
