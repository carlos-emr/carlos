/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.commn.model.ProviderArchive;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verify JPA audit timestamp round trips. Physical Flyway column types and
 * MariaDB migration replay are checked by the OSCAR 19 text-capacity suite.
 *
 * <p>On the Flyway-migrated MariaDB lanes (Ontario and BC) this class also pins the
 * historical roster lookup on {@code demographicArchive}: V1.0.59.50.1 (#5877, bd4c2d1130)
 * creates the {@code (provider_no, roster_date)} index that the as-of roster report's
 * candidate query filters on, and V1.0.59.50.2 (ffe483012d) keeps it when it already serves
 * that lookup. A migration that applies but builds nothing, indexes other columns or a
 * column prefix, or leaves a second copy of the lookup fails here (#6336). H2 and the
 * generated MariaDB schema are built from the entity mappings, which declare no such index,
 * so that assertion runs only where the migrations ran.</p>
 */
@Tag("integration")
@Tag("dao")
@Transactional
class ArchiveAuditTimestampIntegrationTest extends CarlosTestBase {
    private static final String ROSTER_LOOKUP = "idx_demographicArchive_provider_roster_date";

    @Autowired
    private DemographicArchiveDao demographicArchiveDao;
    @Autowired
    private ProviderArchiveDao providerArchiveDao;
    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    /**
     * One {@code demographicArchive} index as information_schema.statistics reports it; the
     * columns are listed in key order, each with its prefix length as {@code column(n)} if any.
     */
    private record ArchiveIndex(String name, String nonUnique, String type, String ignored, String columns) {
        boolean leadsWithProviderThenRosterDate() {
            return columns.equals("provider_no,roster_date") || columns.startsWith("provider_no,roster_date,");
        }
    }

    @Test
    void shouldPreserveDemographicArchiveTimeOfDay() {
        DemographicArchive archive = new DemographicArchive();
        Date changed = Date.from(Instant.parse("2026-11-01T14:32:17Z"));
        archive.setLastUpdateDate(changed);
        demographicArchiveDao.persist(archive);
        demographicArchiveDao.flush();
        demographicArchiveDao.refresh(archive);

        assertThat(archive.getLastUpdateDate().getTime())
                .isEqualTo(changed.getTime());
    }

    @Test
    void shouldPreserveProviderArchiveTimeOfDay() {
        ProviderArchive archive = new ProviderArchive();
        Date changed = Date.from(Instant.parse("2026-11-01T14:32:17Z"));
        archive.setLastUpdateDate(changed);
        providerArchiveDao.persist(archive);
        providerArchiveDao.flush();
        providerArchiveDao.refresh(archive);

        assertThat(archive.getLastUpdateDate().getTime())
                .isEqualTo(changed.getTime());
    }

    @Test
    @Tag("flyway-schema")
    @EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
    @EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
    void shouldIndexArchiveByProviderThenRosterDate_onTheFlywayMigratedSchema() {
        // The environment gate, not carlos.test.schema.expected, selects this case: both Flyway
        // lanes export these variables, but the BC step of maven-project.yml sets no such property
        // and its report check refuses skipped cases. The grouping follows
        // scripts/test-schema-policy-mariadb.py TestHistoricalRosterIndex.indexes().
        List<ArchiveIndex> indexes = new JdbcTemplate(dataSource).query("""
                SELECT index_name, non_unique, index_type, ignored,
                       GROUP_CONCAT(CONCAT(column_name, IFNULL(CONCAT('(', sub_part, ')'), ''))
                                    ORDER BY seq_in_index SEPARATOR ',')
                  FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = ?
                 GROUP BY index_name, non_unique, index_type, ignored
                """, (row, rowNumber) -> new ArchiveIndex(row.getString(1), row.getString(2),
                        row.getString(3), row.getString(4), row.getString(5)), "demographicArchive");

        // Non-unique, B-tree, not ignored, and exactly provider_no then roster_date, unprefixed:
        // the full lookup the 1.0.59.50.2 procedure accepts as already serving the report.
        ArchiveIndex lookup = new ArchiveIndex(ROSTER_LOOKUP, "1", "BTREE", "NO", "provider_no,roster_date");
        assertThat(indexes).as("demographicArchive indexes").contains(lookup);
        // A fresh schema converges on one lookup; 1.0.59.50.2 must not add a second copy.
        assertThat(indexes).filteredOn(ArchiveIndex::leadsWithProviderThenRosterDate)
                .as("indexes leading with provider_no, roster_date").containsExactly(lookup);
    }
}
