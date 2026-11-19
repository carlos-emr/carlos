/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The diagnosis codes a chart names are looked up whatever their number: the former 8,192 rows
 * of registry entries and episodes no longer stop the job.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDiagnosisCodeReadSupportUnitTest {
    @BeforeEach
    void openReadTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    }

    @AfterEach
    void closeReadTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void shouldReadEveryRegistryEntryAndEpisode_pastTheFormerLimitOf8192() {
        var em = mock(EntityManager.class);
        var query = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setFlushMode(any())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        // 9,000 rows in a coding system that has no code table here: read, and none looked up.
        var rows = new ArrayList<Object[]>();
        for (int row = 0; row < 9_000; row++)
            rows.add(
                    new Object[] {
                        "local".getBytes(StandardCharsets.UTF_8),
                        ("C" + row).getBytes(StandardCharsets.UTF_8)
                    });
        when(query.getResultList()).thenReturn(rows);

        assertThat(CdsExportDiagnosisCodeReadSupport.read(em, 27)).isEmpty();

        // The statement is not cut off at a count.
        verify(query, never()).setMaxResults(anyInt());
    }
}
