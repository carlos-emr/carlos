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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Uses only authenticated captured zone/rules metadata; missing old captures and version changes require review. */
@Service
public class CdsExportClinicalZoneService {
    public record Bound(String sourceManifestSha256, ZoneId zone, String tzdbVersion) {
        public Bound{if (sourceManifestSha256==null||!sourceManifestSha256.matches("[0-9a-f]{64}")||zone==null||tzdbVersion==null||tzdbVersion.isEmpty())throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_ZONE_BINDING_INVALID"); }
        @Override public String toString(){return "CdsExportClinicalZoneService.Bound[protected]"; }
    }
    private final CdsExportNativeCaptureService captures; private final CdsExportCapturedTextRowsReader rows;
    @Autowired public CdsExportClinicalZoneService(CdsExportNativeCaptureService captures){this(captures, new CdsExportCapturedTextRowsReader(captures)); }
    CdsExportClinicalZoneService(CdsExportNativeCaptureService captures, CdsExportCapturedTextRowsReader rows){this.captures=Objects.requireNonNull(captures); this.rows=Objects.requireNonNull(rows); }
    public Bound read(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> tables, CdsExportPatientCaptureDto source, long maximumFields, long maximumBytes, CdsExportRunControl control)throws IOException {
        return read(lease, patient, tables, source.manifest(), source.capture(), maximumFields, maximumBytes, control);
    }
    public Bound read(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> tables, CdsExportStagedFileDto manifest, long maximumFields, long maximumBytes, CdsExportRunControl control)throws IOException {
        return read(lease, patient, tables, manifest, null, maximumFields, maximumBytes, control);
    }
    private Bound read(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> tables, CdsExportStagedFileDto manifest, CdsExportNativeCaptureDto expected, long maximumFields, long maximumBytes, CdsExportRunControl control)throws IOException {
        patient.require(lease); control.checkpoint(); if (!tables.contains("clinical_timezone")||manifest==null||!"SOURCE_MANIFEST".equals(manifest.purpose()))invalid();
        var columns=Set.of("sha256", "tzdbVersion", "zoneId"); captures.authorizeSources(lease, patient, tables, control); var found=new CdsExportCapturedTextRowDto[1];
        var actual=rows.forEach(lease, patient, tables, "clinical_timezone", manifest, maximumFields, maximumBytes, 4096, control, row-> {
            if (found[0]!=null||!row.table().equals("clinical_timezone")||!row.recordKey().equals("configuration")||!row.exactNumbers().isEmpty()||!row.fields().keySet().equals(columns))invalid(); found[0]=row;
        });
        if (!tables.equals(actual.tables().keySet())||expected!=null&&!actual.equals(expected)||found[0]==null)invalid();
        var table=actual.tables().get("clinical_timezone"); if (table.rows()!=1||!table.columns().equals(columns)||!table.receiptColumns().isEmpty())invalid();
        var values=found[0].fields(); var zone=CdsExportClinicalZone.restore(values.get("zoneId"), values.get("tzdbVersion"), values.get("sha256"));
        captures.authorizeSources(lease, patient, tables, control); patient.require(lease); control.checkpoint(); return new Bound(manifest.plaintextSha256(), zone, values.get("tzdbVersion"));
    }
    private static void invalid()throws IOException{throw new IOException("CDS_EXPORT_CLINICAL_ZONE_CAPTURE_INVALID"); }
}
