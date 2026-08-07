/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingONCHeader1DaoImpl")
@Tag("unit")
@Tag("dao")
class BillingONCHeader1DaoImplUnitTest {

    private static final String FIND_FOR_UPDATE_SQL =
            "SELECT * FROM billing_on_cheader1 WHERE id = ?1 FOR UPDATE";

    private BillingONCHeader1DaoImpl dao;
    private EntityManager entityManager;
    private Query query;

    @BeforeEach
    void setUp() {
        dao = new BillingONCHeader1DaoImpl();
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);

        dao.entityManager = entityManager;
    }

    @Test
    void shouldUseMariaDbCompatibleForUpdateSql_whenFindingHeaderForUpdate() {
        BillingONCHeader1 header = new BillingONCHeader1();
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, BillingONCHeader1.class)).thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(header));

        BillingONCHeader1 result = dao.findForUpdate(42);

        assertThat(result).isSameAs(header);
        verify(entityManager).createNativeQuery(FIND_FOR_UPDATE_SQL, BillingONCHeader1.class);
        verify(query).setParameter(1, 42);
        verify(entityManager, never()).find(
                eq(BillingONCHeader1.class),
                eq(42),
                eq(LockModeType.PESSIMISTIC_WRITE));
    }

    @Test
    void shouldReturnNull_whenForUpdateHeaderDoesNotExist() {
        when(entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, BillingONCHeader1.class)).thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(42)).isNull();
    }

    @Test
    void shouldReturnNull_whenForUpdateIdIsNull() {
        assertThat(dao.findForUpdate(null)).isNull();
        verify(entityManager, never()).createNativeQuery(eq(FIND_FOR_UPDATE_SQL), eq(BillingONCHeader1.class));
    }
}
