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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowComparison;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPartialRowComparisonDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Only the reviewed demographics-only cust projection is eligible; this never manufactures a whole-row digest. */
final class CdsExportPartialRowComparator {
    private CdsExportPartialRowComparator() { }
    static CdsExportPartialRowComparisonDto compare(CdsNativeRowState baseline, CdsNativeRowState current) {
        var selected = Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS.columns());
        var full = Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CUST.columns());
        if (baseline == null || current == null || !"demographiccust".equals(current.table()) || !baseline.table().equals(current.table())
                || !baseline.id().equals(current.id()) || !Set.copyOf(current.columns().stream().map(Column::name).toList()).equals(selected)) {
            throw new IllegalArgumentException("CDS_EXPORT_PARTIAL_ROW_COMPARISON_SCOPE_INVALID");
        }
        var indexes = new HashMap<String, Integer>();
        for (int i = 0; i < baseline.columns().size(); i++) indexes.put(baseline.columns().get(i).name(), i);
        var omitted = new HashSet<>(full); omitted.removeAll(selected);
        if (!indexes.keySet().equals(full)) {
            return new CdsExportPartialRowComparisonDto(selected, omitted, new CdsNativeRowComparison(Set.of(), Set.of(), true));
        }
        var columns = new ArrayList<Column>(); var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var column : current.columns()) {
            int index = indexes.get(column.name()); columns.add(baseline.columns().get(index)); cells.add(baseline.cells().get(index));
        }
        var projected = new CdsNativeRowState(baseline.table(), baseline.id(), columns, cells);
        return new CdsExportPartialRowComparisonDto(selected, omitted, CdsNativeRowComparison.compare(projected, current));
    }
}
