/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BillingOnItemPaymentDaoImpl")
@Tag("unit")
@Tag("dao")
class BillingOnItemPaymentDaoImplUnitTest {

    private BillingOnItemPaymentDaoImpl dao;
    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() throws Exception {
        dao = new BillingOnItemPaymentDaoImpl();
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);

        java.lang.reflect.Field entityManagerField = AbstractDaoImpl.class.getDeclaredField("entityManager");
        entityManagerField.setAccessible(true);
        entityManagerField.set(dao, entityManager);

        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq(1), eq(123))).thenReturn(query);
    }

    @Test
    void shouldReturnZero_whenNoPaymentRowExistsForItem() {
        when(query.getSingleResult()).thenThrow(new NoResultException("no payment"));

        assertThat(dao.getAmountPaidByItemId(123)).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    void shouldPropagateRuntimeFailure_whenAmountQueryFailsForReasonOtherThanNoResult() {
        when(query.getSingleResult()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> dao.getAmountPaidByItemId(123))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
    }
}
