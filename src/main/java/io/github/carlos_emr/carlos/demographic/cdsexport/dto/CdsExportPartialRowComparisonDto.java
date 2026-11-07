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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import java.util.Set;

/** Evidence about selected fields only. Omitted fields and the original whole-row receipt remain unverified. */
public record CdsExportPartialRowComparisonDto(Set<String> comparedColumns, Set<String> omittedColumns, CdsNativeRowComparison fields) {
    public CdsExportPartialRowComparisonDto {
        comparedColumns = Set.copyOf(comparedColumns); omittedColumns = Set.copyOf(omittedColumns);
        if (comparedColumns.isEmpty() || omittedColumns.isEmpty() || fields == null || !java.util.Collections.disjoint(comparedColumns, omittedColumns)
                || !comparedColumns.containsAll(fields.changedColumns()) || !comparedColumns.containsAll(fields.unqualifiedColumns())) {
            throw new IllegalArgumentException("CDS_EXPORT_PARTIAL_ROW_COMPARISON_INVALID");
        }
    }
    public boolean unchangedSelectedFields() { return fields.unchangedProjection(); }
    @Override public String toString() { return "CdsExportPartialRowComparisonDto[protected]"; }
}
