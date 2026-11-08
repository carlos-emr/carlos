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
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Captures precision through selected, patient-owned native parent relations; duplicate/unknown formats are retained. */
@Service
public class CdsExportPartialDateSourceReader extends CdsExportPatientTableSourceReader {
    private final PartialDateDao source;
    private final Set<CdsCategory> domains;
    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPartialDateSourceReader(PartialDateDao source) {
        this(source, Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS));
    }
    private CdsExportPartialDateSourceReader(PartialDateDao source, Set<CdsCategory> domains) {
        super(source, CdsExportPatientTableReadSupport.Plan.partialDates(domains), domains);
        this.source = source; this.domains = Set.copyOf(domains);
    }
    @Override public CdsExportNativeSourceReader forRequest(io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        var selected = java.util.EnumSet.noneOf(CdsCategory.class);
        for (var category : Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, CdsCategory.MEDICATIONS_AND_TREATMENTS)) {
            if (command.categories().contains(category)) selected.add(category);
        }
        if (selected.isEmpty()) throw new IllegalStateException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        return new CdsExportPartialDateSourceReader(source, selected);
    }
    @Override public Set<CdsCategory> supportedCategories() {
        return Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
    }
    @Override public long count(int patient) { return source.countCdsExportScopedRows(patient, domains); }
    @Override protected java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> page(int patient, Long after, int limit) {
        return source.findCdsExportScopedRowsAfter(patient, domains, after, limit);
    }
    @Override protected byte[] chunk(int patient, long row, String column, long offset, int length) {
        return source.readCdsExportScopedChunk(patient, domains, row, column, offset, length);
    }
}
