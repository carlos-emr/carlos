/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState.Cell;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CdsExportPatientAlertColumnHistory}. */
@Tag("unit")
@Tag("cds-export")
class CdsExportPatientAlertColumnHistoryUnitTest {

    private static final Column DEMOGRAPHIC_NO = new Column("demographic_no", "INT", 10, 0);
    private static final Column CONTENT = new Column("content", "TEXT", 65535, 0);

    @Test
    void shouldRestateCust3ToAfterDefinition_whenBaselineHasBeforeAndCurrentHasAfter() {
        var baseline = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.BEFORE, CONTENT), "Alert");
        var current = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "Alert");

        var restated = CdsExportPatientAlertColumnHistory.widened(baseline, current);

        assertThat(restated.columns()).isEqualTo(current.columns());
        assertThat(restated.cells()).isEqualTo(baseline.cells());
        var comparison = CdsNativeRowComparison.compare(restated, current);
        assertThat(comparison.schemaChanged()).isFalse();
        assertThat(comparison.unqualifiedColumns()).isEmpty();
        assertThat(comparison.changedColumns()).isEmpty();
    }

    @Test
    void shouldDetectChangedAlertValue_whenWidenedBaselineIsComparedWithEditedCurrentRow() {
        var baseline = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.BEFORE, CONTENT), "Old alert");
        var current = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "New alert");

        var restated = CdsExportPatientAlertColumnHistory.widened(baseline, current);
        var comparison = CdsNativeRowComparison.compare(restated, current);

        assertThat(comparison.schemaChanged()).isFalse();
        assertThat(comparison.changedColumns()).containsExactly("cust3");
    }

    @Test
    void shouldLeaveBaselineUnchanged_whenTableOrDirectionOrPositionOrTypeDoesNotMatch() {
        var baseline = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.BEFORE, CONTENT), "Alert");
        var current = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "Alert");

        // Null inputs
        assertThat(CdsExportPatientAlertColumnHistory.widened(null, current)).isNull();
        assertThat(CdsExportPatientAlertColumnHistory.widened(baseline, null)).isSameAs(baseline);

        // Wrong table
        var otherTableBefore = row("demographic", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.BEFORE, CONTENT), "Alert");
        var otherTableAfter = row("demographic", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "Alert");
        assertThat(CdsExportPatientAlertColumnHistory.widened(otherTableBefore, otherTableAfter))
                .isSameAs(otherTableBefore);
        assertThat(CdsExportPatientAlertColumnHistory.widened(baseline, otherTableAfter))
                .isSameAs(baseline);

        // Reversed direction (TEXT -> VARCHAR) must not be accepted
        assertThat(CdsExportPatientAlertColumnHistory.widened(current, baseline)).isSameAs(current);

        // Different column count
        var shorter = new CdsNativeRowState(
                "demographiccust",
                "27",
                List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER),
                List.of(cell("27"), cell("Alert")));
        assertThat(CdsExportPatientAlertColumnHistory.widened(baseline, shorter)).isSameAs(baseline);

        // Different column position
        var reordered = row("demographiccust", List.of(DEMOGRAPHIC_NO, CONTENT, CdsExportPatientAlertColumnHistory.AFTER), "Alert");
        assertThat(CdsExportPatientAlertColumnHistory.widened(baseline, reordered)).isSameAs(baseline);

        // Non-shipped wider type (MEDIUMTEXT)
        var mediumText = row(
                "demographiccust",
                List.of(DEMOGRAPHIC_NO, new Column("cust3", "MEDIUMTEXT", 16777215, 0), CONTENT),
                "Alert");
        assertThat(CdsExportPatientAlertColumnHistory.widened(baseline, mediumText)).isSameAs(baseline);
    }

    @Test
    void shouldRestateCurrentRowToBeforeDigest_whenDemographiccustHasShippedTextCust3() {
        var baseline = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.BEFORE, CONTENT), "Alert");
        var current = row("demographiccust", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "Alert");

        var before = CdsExportPatientAlertColumnHistory.beforeWidening(current);

        assertThat(before).isNotNull();
        assertThat(before.digest()).isEqualTo(baseline.digest());
        assertThat(CdsExportPatientAlertColumnHistory.beforeWidening(null)).isNull();
        assertThat(CdsExportPatientAlertColumnHistory.beforeWidening(baseline)).isNull();
        assertThat(
                CdsExportPatientAlertColumnHistory.beforeWidening(
                        row("demographic", List.of(DEMOGRAPHIC_NO, CdsExportPatientAlertColumnHistory.AFTER, CONTENT), "Alert")))
                .isNull();
    }

    private static CdsNativeRowState row(String table, List<Column> columns, String alert) {
        return new CdsNativeRowState(
                table, "27", columns, List.of(cell("27"), cell(alert), cell("<unotes>Note</unotes>")));
    }

    private static Cell cell(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new Cell(false, bytes.length, HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)));
    }
}
