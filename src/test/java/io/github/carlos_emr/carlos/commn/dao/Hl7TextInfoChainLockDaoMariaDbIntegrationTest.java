/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The chain locks on a real InnoDB (#6391): each statement walks the index it was written for and
 * not the whole table, because a locking scan under REPEATABLE READ locks every row it passes. The
 * name matches the Dao integration pattern the build runs, and the two variables keep it off H2.
 */
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
class Hl7TextInfoChainLockDaoMariaDbIntegrationTest extends Hl7TextInfoChainLockDaoIntegrationTest {

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** The {@code key} column of the first EXPLAIN row: the index the optimizer chose, or null. */
    private String indexChosenFor(String explain) {
        Object[] row = (Object[]) entityManager.createNativeQuery(explain).getResultList().get(0);
        // EXPLAIN columns: id, select_type, table, type, possible_keys, key, key_len, ref, rows, Extra
        // (a partitions column sits after table on some versions): find "key" by the row's width.
        int keyColumn = row.length >= 12 ? 6 : 5;
        return (String) row[keyColumn];
    }

    @Test
    void shouldUseTheAccessionIndex_whenLockingByAccession() {
        stored("ACC-EXPLAIN", null);

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE accessionNum = 'ACC-EXPLAIN'"))
                .isEqualTo("accession_index");
    }

    @Test
    void shouldUseTheFillerOrderNumberIndex_whenLockingByFillerOrderNumber() {
        stored("ACC-EXPLAIN-2", "FILL-EXPLAIN");

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE filler_order_num = 'FILL-EXPLAIN'"))
                .isEqualTo("idx_hl7TextInfo_filler_order_num");
    }

    @Test
    void shouldUseTheLabNumberIndex_whenLockingTheReportsOwnRow() {
        stored("ACC-EXPLAIN-3", null);

        assertThat(indexChosenFor("EXPLAIN SELECT * FROM hl7TextInfo WHERE lab_no = 101"))
                .isEqualTo("labno_index");
    }

    @Test
    void shouldUseTheKeyValueIndex_whenFindingAReportsMeasurements() {
        assertThat(indexChosenFor("EXPLAIN SELECT * FROM measurementsExt WHERE keyval = 'lab_no' AND val = '101'"))
                .isEqualTo("measurements_ext_keyval_val");
    }
}
