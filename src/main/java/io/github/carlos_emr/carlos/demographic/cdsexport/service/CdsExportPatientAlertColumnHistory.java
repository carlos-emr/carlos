/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import java.util.ArrayList;

/**
 * The one historical column definition the CDS export accepts in place of the current one: the
 * patient alert before migration V1.0.59.54 (#6264).
 *
 * <p>Import receipts, native baselines and export witnesses record each column's JDBC type,
 * precision and scale with its value ({@code CdsNativeRowDigest}). V1.0.59.54 widened
 * {@code demographiccust.cust3} from VARCHAR(255) to TEXT, so a patient's custom-field row
 * receipted by an import before it describes cust3 as {@link #BEFORE} and the same row read now
 * describes it as {@link #AFTER}, with every stored value unchanged: widening a VARCHAR to TEXT in
 * the same character set keeps each value's bytes. Without this rule the export would read every
 * such row as a schema change, and an export selecting Alerts and Special Needs would stop on the
 * first chart imported before the upgrade.
 *
 * <p>Nothing else is accepted: only table {@code demographiccust}, only column {@code cust3}, only
 * from exactly {@link #BEFORE} to exactly {@link #AFTER} (what MariaDB Connector/J reports for the
 * shipped columns), never the other way. Any other definition change stays a schema change for
 * review, and a changed value is still a changed value.
 *
 * @since 2026-11-27
 */
final class CdsExportPatientAlertColumnHistory {
    /** The table whose alert column V1.0.59.54 widened. */
    static final String TABLE = "demographiccust";

    /** cust3 as receipted before V1.0.59.54: VARCHAR(255). */
    static final Column BEFORE = new Column("cust3", "VARCHAR", 255, 0);

    /** cust3 as read after V1.0.59.54: TEXT, 65,535 bytes. */
    static final Column AFTER = new Column("cust3", "TEXT", 65535, 0);

    private CdsExportPatientAlertColumnHistory() {}

    /**
     * The historical row restated with the current alert definition, so that it can be compared
     * with the current row column for column.
     *
     * @param baseline CdsNativeRowState the row as an import receipted it; may be {@code null}
     * @param current CdsNativeRowState the same row as read now; may be {@code null}
     * @return CdsNativeRowState {@code baseline} with cust3 described as {@link #AFTER} when the
     *     two rows are demographiccust rows whose cust3 is {@link #BEFORE} in the baseline and
     *     {@link #AFTER} now, at the same position; otherwise {@code baseline} itself, unchanged
     */
    static CdsNativeRowState widened(CdsNativeRowState baseline, CdsNativeRowState current) {
        if (baseline == null
                || current == null
                || !TABLE.equals(baseline.table())
                || !TABLE.equals(current.table())
                || baseline.columns().size() != current.columns().size()) return baseline;
        int position = baseline.columns().indexOf(BEFORE);
        if (position < 0 || !AFTER.equals(current.columns().get(position))) return baseline;
        return restated(baseline, position, AFTER);
    }

    /**
     * The current row as it would have been receipted before V1.0.59.54. Its digest equals the
     * receipt digest of a row receipted then exactly when no value has changed since.
     *
     * @param current CdsNativeRowState the row as read now; may be {@code null}
     * @return CdsNativeRowState {@code current} with cust3 described as {@link #BEFORE}, or
     *     {@code null} when {@code current} is not a demographiccust row with cust3 described as
     *     {@link #AFTER}
     */
    static CdsNativeRowState beforeWidening(CdsNativeRowState current) {
        if (current == null || !TABLE.equals(current.table())) return null;
        int position = current.columns().indexOf(AFTER);
        return position < 0 ? null : restated(current, position, BEFORE);
    }

    private static CdsNativeRowState restated(CdsNativeRowState row, int position, Column column) {
        var columns = new ArrayList<>(row.columns());
        columns.set(position, column);
        return new CdsNativeRowState(row.table(), row.id(), columns, row.cells());
    }
}
