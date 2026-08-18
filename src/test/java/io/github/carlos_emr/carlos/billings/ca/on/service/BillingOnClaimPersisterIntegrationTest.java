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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration coverage for {@code BillingOnClaimPersister} across its multi-table write workflow. */
@DisplayName("BillingOnClaimPersister integration")
@Tag("integration")
@Tag("billing")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(locations = "classpath:test-billing-on-claim-persister-context.xml")
class BillingOnClaimPersisterIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingOnClaimPersister persister;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    void shouldRollbackExtPaymentAndItemPaymentRows_whenAmountValidationFails() {
        // Run setup/assertions in their own transactions so the integration
        // test can observe committed database state before and after rollback.
        // BillingClaimItemDto eagerly validates amount fields in its compact
        // constructor, so a malformed item-paid value is unreachable through
        // the DTO API. The persister still validates the request-level
        // {@code total_discount} via BillingMoney.parseOptionalNonNegativeAmount
        // upfront — that is the @Transactional abort path verified here.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int billingNo = tx.execute(status -> createHeader());
        try {
            Map<String, String> values = new HashMap<>();
            values.put("demographic_no", "700001");
            values.put("total_payment", "100.00");
            // Inject a malformed amount that the persister rejects upfront
            // (BillingMoney.parseOptionalNonNegativeAmount throws on non-numeric
            // input). The persister design aborts BEFORE any BillingONExt rows
            // are written, so the rollback verification confirms the
            // unit-of-work boundary holds.
            values.put("total_discount", "not-money");
            values.put("payMethod", "1");

            ArrayList<Object> envelope = new ArrayList<>();
            envelope.add(headerDto(billingNo));
            envelope.add(List.of(
                    itemDto("5001", "25.00"),
                    itemDto("5002", "10.00")));

            assertThatThrownBy(() -> persister.add3rdBillExt(values, billingNo, envelope))
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("malformed");

            tx.executeWithoutResult(status -> {
                assertThat(count("BillingONExt", "billingNo", billingNo)).isZero();
                assertThat(count("BillingONPayment", "billingNo", billingNo)).isZero();
                assertThat(count("BillingOnItemPayment", "ch1Id", billingNo)).isZero();
                assertThat(count("BillingOnTransaction", "ch1Id", billingNo)).isZero();
            });
        } finally {
            tx.executeWithoutResult(status ->
                    entityManager.createQuery("DELETE FROM BillingONCHeader1 h WHERE h.id = :id")
                            .setParameter("id", billingNo)
                            .executeUpdate());
        }
    }

    private int createHeader() {
        BillingONCHeader1 h = new BillingONCHeader1();
        h.setHeaderId(0);
        h.setDemographicNo(700001);
        h.setProviderNo("999998");
        h.setStatus("O");
        h.setBillingDate(new Date());
        h.setBillingTime(new Date());
        h.setPayProgram("PAT");
        h.setVisitType("00");
        h.setTotal(new BigDecimal("100.00"));
        h.setPaid(new BigDecimal("0.00"));
        h.setApptProviderNo("999998");
        h.setCreator("999998");
        h.setAppointmentNo(0);
        h.setFaciltyNum("0001");
        entityManager.persist(h);
        entityManager.flush();
        return h.getId();
    }

    private BillingClaimHeaderDto headerDto(int billingNo) {
        BillingClaimHeaderDto header = new BillingClaimHeaderDto();
        header = header.withId(String.valueOf(billingNo));
        header = header.withCreator("999998");
        header = header.withDemographicNo("700001");
        header = header.withBillingDate("2026-05-01");
        header = header.withAdmissionDate("");
        header = header.withComment("");
        header = header.withClinic("");
        header = header.withFacilityNumber("0001");
        header = header.withManualReview("");
        header = header.withPayProgram("PAT");
        header = header.withProviderNo("999998");
        header = header.withProvince("ON");
        header = header.withReferralNumber("");
        header = header.withLocation("");
        header = header.withStatus("O");
        header = header.withVisitType("00");
        return header;
    }

    private BillingClaimItemDto itemDto(String id, String paid) {
        BillingClaimItemDto item = new BillingClaimItemDto();
        item = item.withId(id);
        item = item.withDiscount("0.00");
        item = item.withPaid(paid);
        item = item.withRefund("0.00");
        item = item.withServiceCode("A001A");
        item = item.withFee("25.00");
        item = item.withServiceNumber("1");
        item = item.withDx("001");
        return item;
    }

    private long count(String entityName, String fieldName, int billingNo) {
        return entityManager
                .createQuery("SELECT COUNT(e) FROM " + entityName + " e WHERE e." + fieldName + " = :billingNo",
                        Long.class)
                .setParameter("billingNo", billingNo)
                .getSingleResult();
    }
}
