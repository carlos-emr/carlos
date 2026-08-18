/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.Query;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BatchBillingDaoImpl")
@Tag("unit")
@Tag("dao")
class BatchBillingDaoImplUnitTest {

    private static final String FIND_FOR_UPDATE_SQL =
            "SELECT * FROM batch_billing WHERE demographic_no = ?1 AND service_code = ?2 FOR UPDATE";

    private BatchBillingDaoImpl dao;
    private Query query;

    @BeforeEach
    void setUp() {
        dao = new BatchBillingDaoImpl();
        dao.entityManager = mock(jakarta.persistence.EntityManager.class);
        query = mock(Query.class);
    }

    @Test
    void shouldUseMariaDbCompatibleForUpdateSql_whenFindingBatchRowsForUpdate() {
        BatchBilling batchBilling = new BatchBilling();
        when(dao.entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, BatchBilling.class)).thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.setParameter(2, "A007A")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(batchBilling));

        List<BatchBilling> result = dao.findForUpdate(42, "A007A");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(batchBilling);
        verify(dao.entityManager).createNativeQuery(FIND_FOR_UPDATE_SQL, BatchBilling.class);
        verify(query).setParameter(1, 42);
        verify(query).setParameter(2, "A007A");
    }

    @Test
    void shouldReturnEmptyList_whenNoBatchRowsExistForUpdate() {
        when(dao.entityManager.createNativeQuery(FIND_FOR_UPDATE_SQL, BatchBilling.class)).thenReturn(query);
        when(query.setParameter(1, 42)).thenReturn(query);
        when(query.setParameter(2, "A007A")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertThat(dao.findForUpdate(42, "A007A")).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenForUpdateLookupCannotMatchAnyRow() {
        assertThat(dao.findForUpdate(null, "A007A")).isEmpty();
        assertThat(dao.findForUpdate(42, null)).isEmpty();

        verify(dao.entityManager, never()).createNativeQuery(anyString(), org.mockito.ArgumentMatchers.<Class<?>>any());
    }
}
