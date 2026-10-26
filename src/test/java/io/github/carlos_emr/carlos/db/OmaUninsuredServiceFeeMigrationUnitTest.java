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

package io.github.carlos_emr.carlos.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Static checks on the Ontario migration that adds the OMA uninsured service fees to the
 * PRIVATE billing form (issue #3894, OntarioMD PC13.19).
 *
 * <p>A migration cannot be edited once released, so the data shape is pinned here before it
 * ships: every fee code fits {@code billingservice.service_code} (VARCHAR(10)) and is mapped to
 * exactly one PRIVATE-form group, and every insert stays existence-guarded because neither
 * table has a natural unique key.</p>
 */
@Tag("unit")
@Tag("database")
@Tag("billing")
@DisplayName("OMA uninsured service fee migration")
class OmaUninsuredServiceFeeMigrationUnitTest {

    private static final Path MIGRATION = Path.of("database", "mysql", "migration", "on",
            "V1.0.34__add_oma_uninsured_service_fees.sql");

    /** ('_OMA_X', 'description', '12.34', '2026-01-01') rows of the fee staging table. */
    private static final Pattern FEE_ROW = Pattern.compile(
            "\\('(_OMA_[A-Z0-9]+)', '((?:[^']|'')*)', '([^']*)', '(\\d{4}-\\d{2}-\\d{2})'\\)");
    /** ('_OMA_X', 'Forms', 'Group1', 3) rows of the form-mapping staging table. */
    private static final Pattern MAP_ROW = Pattern.compile(
            "\\('(_OMA_[A-Z0-9]+)', '(Forms|Assessments|Procedures)', '(Group[123])', (\\d+)\\)");

    private static String sql;
    private static final Map<String, String> FEES = new LinkedHashMap<>();
    private static final Map<String, String> GROUPS = new LinkedHashMap<>();

    @BeforeAll
    static void loadMigration() throws Exception {
        sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        Matcher fee = FEE_ROW.matcher(sql);
        while (fee.find()) {
            FEES.put(fee.group(1), fee.group(3));
        }
        Matcher map = MAP_ROW.matcher(sql);
        while (map.find()) {
            GROUPS.put(map.group(1), map.group(2) + "/" + map.group(3));
        }
    }

    @Test
    @DisplayName("should add all 34 OMA fee codes within the service_code column width")
    void shouldDefineThirtyFourCodes_withinServiceCodeWidth() {
        assertThat(FEES).hasSize(34);
        assertThat(FEES.keySet()).allSatisfy(code -> assertThat(code.length()).isLessThanOrEqualTo(10));
    }

    @Test
    @DisplayName("should store every fee as a plain decimal amount that fits billingservice.value")
    void shouldUsePlainDecimalFees_forEveryCode() {
        assertThat(FEES.values()).allSatisfy(value -> {
            assertThat(value).matches("\\d{1,5}\\.\\d{2}");
            assertThat(value.length()).isLessThanOrEqualTo(8);
        });
    }

    @Test
    @DisplayName("should map every fee code to exactly one PRIVATE form group and nothing else")
    void shouldMapEveryCode_toOnePrivateFormGroup() {
        assertThat(GROUPS.keySet()).containsExactlyInAnyOrderElementsOf(FEES.keySet());
        Set<String> pairs = new LinkedHashSet<>(GROUPS.values());
        assertThat(pairs).containsExactlyInAnyOrder(
                "Forms/Group1", "Assessments/Group2", "Procedures/Group3");
    }

    @Test
    @DisplayName("should update stale fee rows for the same code and date before inserting missing ones")
    void shouldUpsertFeeRows_beforeInsertingMissingOnes() {
        int update = sql.indexOf("UPDATE `billingservice` AS bs");
        int insert = sql.indexOf("INSERT INTO `billingservice`");

        assertThat(update).isNotNegative().isLessThan(insert);
        String updateStatement = sql.substring(update, sql.indexOf(';', update));
        assertThat(updateStatement)
                .contains("ON bs.`service_code` = fee.`service_code`")
                .contains("AND bs.`billingservice_date` = fee.`billingservice_date`")
                .contains("bs.`description`      = fee.`description`")
                .contains("bs.`value`            = fee.`value`")
                .contains("bs.`region`           = NULL")
                // Clinic-configured flags are preserved by policy.
                .doesNotContain("gstFlag")
                .doesNotContain("sliFlag")
                .doesNotContain("displaystyle");
    }

    @Test
    @DisplayName("should guard both inserts and only deactivate the seeded placeholder mappings")
    void shouldStayIdempotent_forRerun() {
        assertThat(sql)
                .contains("AND bs.`billingservice_date` = fee.`billingservice_date`")
                .contains("WHERE ctl.`servicetype` = 'PRI'")
                .contains("AND `servicetype_name` = 'PRIVATE'")
                .contains("AND `service_order` = 1")
                .contains("`service_group` = 'Group1' AND `service_group_name` = ' Group 1 Name'")
                .contains("`service_group` = 'Group2' AND `service_group_name` = ' Group 2 Name'")
                .contains("`service_group` = 'Group3' AND `service_group_name` = ' Group 3 Name'")
                .doesNotContainIgnoringCase("DELETE FROM");
    }
    @Test
    void shouldDistinguishHourlyRatesAndReimbursements_whenSeedingVerifiedFees() {
        assertThat(FEES).containsEntry("_OMA_F08", "160.00")
                .containsEntry("_OMA_F18", "497.00")
                .containsEntry("_OMA_F19", "497.00")
                .containsEntry("_OMA_G010", "7.70");
        assertThat(sql).contains("$497/hour; enter time-based total")
                .contains("hourly; minimum $160")
                .contains("Service Canada reimbursement limit; OMA minimum total $200")
                .contains("Service Canada reimbursement limit; OMA minimum total $135")
                .contains("clinic default; set hourly total");
    }

}
