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

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link FluBillingPersistenceService} — the atomic
 * Billing+BillingDetail persist pair. Pins:
 * <ul>
 *   <li>parent persists before child, and the FK is stamped from the
 *       generated parent id on the child</li>
 *   <li>a child-persist failure propagates so the @Transactional proxy can
 *       roll the parent back</li>
 * </ul>
 */
@DisplayName("FluBillingPersistenceService")
@Tag("unit")
@Tag("billing")
class FluBillingPersistenceServiceUnitTest extends CarlosUnitTestBase {

    @Mock private BillingDao billingDao;
    @Mock private BillingDetailDao billingDetailDao;

    private FluBillingPersistenceService svc;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        svc = new FluBillingPersistenceService(billingDao, billingDetailDao);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldPersistBillingThenDetailWithGeneratedFk_whenPersistFluBilling() {
        Billing billing = new Billing();
        BillingDetail detail = new BillingDetail();

        // Simulate Hibernate id-generation: Billing has no public setId, so
        // reflect onto the field directly to mimic the post-persist state.
        doAnswer(inv -> {
            assignIdField(inv.getArgument(0), 4242);
            return null;
        }).when(billingDao).persist(same(billing));

        Integer returnedId = svc.persistFluBilling(billing, detail);

        assertThat(returnedId).isEqualTo(4242);
        // FK stamped from the parent's generated id BEFORE the detail persist.
        assertThat(detail.getBillingNo()).isEqualTo(4242);
        InOrder order = inOrder(billingDao, billingDetailDao);
        order.verify(billingDao).persist(same(billing));
        order.verify(billingDetailDao).persist(same(detail));
    }

    @Test
    void shouldPropagate_whenDetailPersistThrows() {
        Billing billing = new Billing();
        BillingDetail detail = new BillingDetail();
        doAnswer(inv -> {
            assignIdField(inv.getArgument(0), 7);
            return null;
        }).when(billingDao).persist(same(billing));
        doThrow(new RuntimeException("detail-fail"))
                .when(billingDetailDao).persist(same(detail));

        assertThatThrownBy(() -> svc.persistFluBilling(billing, detail))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("detail-fail");

        // Parent persist DID happen — Spring's @Transactional rolls it back, NOT the service.
        verify(billingDao).persist(same(billing));
    }

    private static void assignIdField(Object entity, int id) {
        try {
            java.lang.reflect.Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Billing.id field structure changed?", e);
        }
    }

    @Test
    void shouldPropagate_whenParentPersistThrows() {
        Billing billing = new Billing();
        BillingDetail detail = new BillingDetail();
        doThrow(new RuntimeException("parent-fail"))
                .when(billingDao).persist(same(billing));

        assertThatThrownBy(() -> svc.persistFluBilling(billing, detail))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("parent-fail");

        verify(billingDetailDao, never()).persist(same(detail));
    }
}
