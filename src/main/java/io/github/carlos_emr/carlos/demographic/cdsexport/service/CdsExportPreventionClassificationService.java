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
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** Complete frozen metadata classification after both authenticated EOFs, protected configuration and final live authority. No clinical serialization or coverage claim. */
@Service
public class CdsExportPreventionClassificationService {
    private final CdsExportNativeCaptureService captures; private final CdsExportPreventionConfigurationService configuration; private final CdsExportCapturedTextRowsReader rows;
    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPreventionClassificationService(CdsExportNativeCaptureService captures, CdsExportPreventionConfigurationService configuration){this(captures, configuration, new CdsExportCapturedTextRowsReader(captures)); }
    CdsExportPreventionClassificationService(CdsExportNativeCaptureService captures, CdsExportPreventionConfigurationService configuration, CdsExportCapturedTextRowsReader rows){this.captures=Objects.requireNonNull(captures); this.configuration=Objects.requireNonNull(configuration); this.rows=Objects.requireNonNull(rows); }
    public CdsExportPreventionScopePlanner.Result classify(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, CdsExportPreventionConfigurationDto configured, long maximumFields, long maximumBytes, CdsExportRunControl control)throws IOException{
        return classify(lease, patient, requiredTables, source, configured, maximumFields, maximumBytes, control, false);
    }
    public CdsExportPreventionScopePlanner.Result classifyEmbedded(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, long maximumFields, long maximumBytes, CdsExportRunControl control)throws IOException{
        return classify(lease, patient, requiredTables, source, null, maximumFields, maximumBytes, control, true);
    }
    private CdsExportPreventionScopePlanner.Result classify(CdsExportLeaseDto lease, CdsExportCohortService.Patient patient, Set<String> requiredTables,
            CdsExportPatientCaptureDto source, CdsExportPreventionConfigurationDto configured, long maximumFields, long maximumBytes, CdsExportRunControl control, boolean embedded)throws IOException{
        patient.require(lease); if (!requiredTables.contains("preventions_scope")||!requiredTables.equals(source.capture().tables().keySet()))throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        var classifier=embedded?configuration.readEmbedded(lease, patient, requiredTables, source, maximumFields, maximumBytes, control):configuration.restore(lease, patient, requiredTables, source, configured, maximumFields, maximumBytes, control); if (!source.manifest().plaintextSha256().equals(classifier.sourceManifestSha256()))throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID"); var metadata=new ArrayList<CdsExportCapturedTextRowDto>(); long[] bytes={0};
        var actual=rows.forEach(lease, patient, requiredTables, "preventions_scope", source.manifest(), maximumFields, maximumBytes, 4096, control, row-> {
            if (metadata.size()>=10_000)throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_LIMIT"); for (String value:row.fields().values())if (value!=null){bytes[0]+=value.getBytes(StandardCharsets.UTF_8).length; if (bytes[0]>4_194_304)throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_LIMIT"); }metadata.add(row);
        });
        if (!actual.equals(source.capture()))throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        var result=new CdsExportPreventionScopePlanner().plan(patient.patient(), classifier, metadata);
        captures.authorizeSources(lease, patient, requiredTables, control); patient.require(lease); control.checkpoint(); return result;
    }
}
