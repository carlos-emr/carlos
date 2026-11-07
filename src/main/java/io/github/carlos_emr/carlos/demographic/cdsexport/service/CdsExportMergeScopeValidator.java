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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.Set;

/** Proves a frozen generation has no active direct merge links. This is not an alias-expansion implementation or whole-chart coverage proof. */
public final class CdsExportMergeScopeValidator {
    private final CdsExportCapturedTextRowsReader rows;
    public CdsExportMergeScopeValidator(CdsExportNativeCaptureService captures) { this(new CdsExportCapturedTextRowsReader(captures)); }
    CdsExportMergeScopeValidator(CdsExportCapturedTextRowsReader rows) { this.rows = java.util.Objects.requireNonNull(rows); }
    public void requireUnmerged(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> tables,
            CdsExportPatientCaptureDto source, long maximumFields, long maximumBytes, CdsExportRunControl control) throws IOException {
        patient.require(lease); control.checkpoint();
        if (!tables.equals(source.capture().tables().keySet()) || !tables.contains("demographic_merged")) scope();
        boolean[] review = {false}; long[] count = {0};
        var actual = rows.forEach(lease, patient, tables, "demographic_merged", source.manifest(), maximumFields, maximumBytes, 4096, control, row -> {
            if (!row.table().equals("demographic_merged") || !row.fields().keySet().equals(Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()))) scope();
            int from = owner(row.fields().get("demographic_no")), to = owner(row.fields().get("merged_to"));
            if (from != patient.patient() && to != patient.patient()) scope();
            try {
                String text = row.fields().get("id"); long id = Long.parseLong(text);
                if (!Long.toString(id).equals(text) || !CdsExportPatientTableSourceReader.key(id).equals(row.recordKey())) scope();
            } catch (NumberFormatException invalid) { scope(); }
            String deleted = row.fields().get("deleted"); if (!"1".equals(deleted)) review[0] = true;
            count[0]++;
        });
        if (!actual.equals(source.capture()) || count[0] != source.capture().tables().get("demographic_merged").rows()) scope();
        patient.require(lease); control.checkpoint();
        // Complete source authentication and fresh authority precede even an adverse review result.
        if (review[0]) throw new CdsExportReviewException(CdsExportReviewException.Reason.MERGED_PATIENT_SCOPE);
    }
    private static int owner(String text) throws IOException {
        try { int value = Integer.parseInt(text); if (value < 1 || !Integer.toString(value).equals(text)) scope(); return value; }
        catch (NumberFormatException invalid) { throw new IOException("CDS_EXPORT_MERGE_SCOPE_INVALID"); }
    }
    private static void scope() throws IOException { throw new IOException("CDS_EXPORT_MERGE_SCOPE_INVALID"); }
}
