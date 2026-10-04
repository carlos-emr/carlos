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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real commit/rollback and competing-settlement checks for the service transaction. */
@Tag("integration")
@Tag("billing")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingCorrectionSettlementTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private BillingONCHeader1Dao headers;
    @Autowired private BillingONPaymentDao payments;
    @Autowired private BillingONExtDao extensions;
    @Autowired private BillingPaymentTypeDao paymentTypes;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldRollbackStatusPaymentAndInvoiceBalance_whenExtensionWriteFailsAfterFlush() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int id = tx.execute(status -> createInvoice());
        try {
            BillingONExtDao failing = mock(BillingONExtDao.class, delegatesTo(extensions));
            doAnswer(invocation -> {
                extensions.setExtItem(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5));
                entityManager.flush();
                throw new IllegalStateException("synthetic persistence failure");
            }).when(failing).setExtItem(anyInt(), anyInt(), anyString(), anyString(), any(Date.class), anyChar());
            BillingCorrectionService service = transactionalService(payments, failing);
            assertThatThrownBy(() -> service.updateInvoice(operator(), request(id)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("synthetic persistence failure");
            tx.executeWithoutResult(status -> {
                assertThat(headers.find(id).getStatus()).isEqualTo("O");
                assertThat(headers.find(id).getPaid()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(payments.listPaymentsByBillingNo(id)).isEmpty();
                assertThat(extensions.findByBillingNoAndKey(id, BillingONExtDao.KEY_PAYMENT)).isEmpty();
            });
        } finally {
            tx.executeWithoutResult(status -> deleteInvoice(id));
        }
    }

    @Test
    void shouldRecordOnePayment_whenSettlementRequestsOverlap() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int id = tx.execute(status -> createInvoice());
        CountDownLatch firstPersisted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            BillingONPaymentDao delayed = mock(BillingONPaymentDao.class, delegatesTo(payments));
            doAnswer(invocation -> {
                payments.persist(invocation.getArgument(0));
                firstPersisted.countDown();
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
                return null;
            }).when(delayed).persist(any(BillingONPayment.class));
            BillingCorrectionService firstService = transactionalService(delayed);
            BillingCorrectionService secondService = transactionalService(payments);
            var first = executor.submit(() -> firstService.updateInvoice(operator(), request(id)));
            try {
                assertThat(firstPersisted.await(10, TimeUnit.SECONDS)).isTrue();
                CountDownLatch secondStarted = new CountDownLatch(1);
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return secondService.updateInvoice(operator(), request(id));
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("submitClose");
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("submitClose");
            } finally {
                releaseFirst.countDown();
            }
            tx.executeWithoutResult(status -> {
                assertThat(payments.listPaymentsByBillingNo(id)).hasSize(1);
                assertThat(headers.find(id).getPaid()).isEqualByComparingTo("50.00");
                assertThat(headers.find(id).getStatus()).isEqualTo("S");
            });
        } finally {
            releaseFirst.countDown();
            tx.executeWithoutResult(status -> deleteInvoice(id));
        }
    }

    private BillingCorrectionService transactionalService(BillingONPaymentDao paymentDao) {
        return transactionalService(paymentDao, extensions);
    }

    private BillingCorrectionService transactionalService(BillingONPaymentDao paymentDao, BillingONExtDao extensionDao) {
        ProviderDao providers = mock(ProviderDao.class);
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        provider.setOhipNo("123456");
        provider.setRmaNo("0001");
        when(providers.getProvider("999998")).thenReturn(provider);
        BillingCorrectionService target = new BillingCorrectionService(paymentDao, headers, extensionDao,
                paymentTypes, mock(BillingONRepoDao.class), providers, mock(BillingServiceDao.class));
        TransactionInterceptor transactions = new TransactionInterceptor();
        transactions.setTransactionManager(transactionManager);
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(transactions);
        return (BillingCorrectionService) proxy.getProxy();
    }

    private LoggedInInfo operator() {
        LoggedInInfo operator = mock(LoggedInInfo.class);
        when(operator.getLoggedInProviderNo()).thenReturn("999998");
        return operator;
    }

    private int createInvoice() {
        BillingONCHeader1 bill = new BillingONCHeader1();
        bill.setHeaderId(0);
        bill.setDemographicNo(700052);
        bill.setProviderNo("999998");
        bill.setStatus("O");
        bill.setTotal(new BigDecimal("50.00"));
        bill.setPaid(BigDecimal.ZERO);
        bill.setBillingDate(new Date());
        bill.setBillingTime(new Date());
        entityManager.persist(bill);
        entityManager.flush();
        return bill.getId();
    }

    private void deleteInvoice(int id) {
        entityManager.createQuery("DELETE FROM BillingONExt e WHERE e.billingNo = :id").setParameter("id", id).executeUpdate();
        entityManager.createQuery("DELETE FROM BillingONPayment p WHERE p.billingNo = :id").setParameter("id", id).executeUpdate();
        entityManager.createQuery("DELETE FROM BillingONCHeader1 h WHERE h.id = :id").setParameter("id", id).executeUpdate();
    }

    private MockHttpServletRequest request(int id) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.CANADA));
        request.setParameter("xml_billing_no", String.valueOf(id));
        request.setParameter("status", "S");
        request.setParameter("oldStatus", "O");
        request.setParameter("payProgram", "HCP");
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
        return request;
    }
}
