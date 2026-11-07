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

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Exact physical demographic row; contact/extension/pharmacy/history tables need separate readers. */
@Service
public class CdsExportDemographicSourceReader implements CdsExportNativeSourceReader {
    private final DemographicDao demographics;
    public CdsExportDemographicSourceReader(DemographicDao demographics) { this.demographics = demographics; }
    @Override public String table() { return "demographic"; }
    @Override public Set<String> columns() {
        var names = new HashSet<>(CdsExportDemographicRowDto.COLUMNS); names.add("demographic_no"); return Set.copyOf(names);
    }
    @Override public Set<CdsCategory> categories() { return Set.of(CdsCategory.DEMOGRAPHICS); }
    @Override public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> receiptColumns() {
        return demographics.readCdsExportReceiptColumns();
    }
    @Override public CdsExportTableSchemaDto schema() { return demographics.readCdsExportDemographicSchema(); }
    @Override public long count(int patient) { return demographics.findCdsExportSelectedPatients(List.of(patient)).size(); }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output) throws IOException {
        var row = demographics.findCdsExportRawDemographic(patient);
        if (row == null) throw new IOException("CDS_EXPORT_SELECTED_PATIENT_MISSING");
        if (row.demographicNo() != patient) throw new IOException("CDS_EXPORT_CAPTURE_SCOPE_MISMATCH");
        var fields = new LinkedHashMap<>(row.fields()); fields.put("demographic_no", Integer.toString(patient));
        output.row(String.format(Locale.ROOT, "%010d", patient), fields);
    }
}
