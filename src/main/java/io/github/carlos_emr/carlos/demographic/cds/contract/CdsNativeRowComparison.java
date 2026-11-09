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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Mechanical SQL-projection differences, kept separate from a clinical precedence decision. */
public record CdsNativeRowComparison(
        Set<String> changedColumns, Set<String> unqualifiedColumns, boolean schemaChanged) {
    public CdsNativeRowComparison {
        changedColumns = Set.copyOf(changedColumns);
        unqualifiedColumns = Set.copyOf(unqualifiedColumns);
    }

    public static CdsNativeRowComparison compare(
            CdsNativeRowState baseline, CdsNativeRowState current) {
        if (baseline == null
                || current == null
                || !baseline.table().equals(current.table())
                || !baseline.id().equals(current.id()))
            throw new IllegalArgumentException("CDS_NATIVE_ROW_COMPARISON_SCOPE_INVALID");
        if (!baseline.columns().equals(current.columns()))
            return new CdsNativeRowComparison(Set.of(), Set.of(), true);
        var changed = new HashSet<String>();
        var unqualified = new HashSet<String>();
        for (int i = 0; i < baseline.columns().size(); i++) {
            var column = baseline.columns().get(i);
            var previous = baseline.cells().get(i);
            var actual = current.cells().get(i);
            var codec = CdsExactNumeric.codec(column.type());
            boolean numeric = codec != null;
            boolean exact =
                    numeric
                            && (previous.sqlNull() || previous.exactNumeric() != null)
                            && (actual.sqlNull() || actual.exactNumeric() != null);
            if (exact) {
                if (previous.sqlNull() != actual.sqlNull()
                        || !java.util.Objects.equals(
                                previous.exactNumeric(), actual.exactNumeric()))
                    changed.add(column.name());
            } else if (previous.sqlNull() != actual.sqlNull()
                    || previous.bytes() != actual.bytes()
                    || !java.util.Objects.equals(previous.sha256(), actual.sha256()))
                changed.add(column.name());
            String type = column.type().toUpperCase(Locale.ROOT);
            if ((!previous.sqlNull() || !actual.sqlNull())
                    && (numeric && !exact || type.startsWith("TIMESTAMP")))
                unqualified.add(column.name());
        }
        return new CdsNativeRowComparison(changed, unqualified, false);
    }

    public boolean unchangedProjection() {
        return !schemaChanged && changedColumns.isEmpty() && unqualifiedColumns.isEmpty();
    }

    @Override
    public String toString() {
        return "CdsNativeRowComparison[protected]";
    }
}
