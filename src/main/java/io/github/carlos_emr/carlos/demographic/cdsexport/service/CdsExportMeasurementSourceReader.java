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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Complete patient-owned current observations other than laboratory-filed HL7 results, which the
 * capture plan leaves to the LaboratoryResults path. When that path is selected, a filed result
 * naming no report it can export holds the patient before any observation value is read.
 */
@Service
public class CdsExportMeasurementSourceReader extends CdsExportPatientTableSourceReader {
    private final MeasurementDao source;
    private final boolean laboratories;

    @Autowired
    public CdsExportMeasurementSourceReader(MeasurementDao source) {
        this(source, false);
    }

    private CdsExportMeasurementSourceReader(MeasurementDao source, boolean laboratories) {
        super(
                source,
                CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_CURRENT,
                Set.of(CdsCategory.CARE_ELEMENTS));
        this.source = Objects.requireNonNull(source);
        this.laboratories = laboratories;
    }

    @Override
    public CdsExportNativeSourceReader forRequest(CdsExportCommand command) {
        return new CdsExportMeasurementSourceReader(
                source,
                Objects.requireNonNull(command)
                        .categories()
                        .contains(CdsCategory.LABORATORY_RESULTS));
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("measurements", "measurementsExt", "measurementsDeleted", "measurementType");
    }

    /**
     * With LaboratoryResults selected, a laboratory-filed result that path cannot export would
     * otherwise leave the package without notice; without it the operator excluded laboratory
     * results, so the filed copies are deliberately not exported.
     */
    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        if (laboratories && source.hasCdsExportUnroutedLaboratoryMeasurements(patient))
            throw new CdsExportMappingReviewException(
                    "CDS_EXPORT_NATIVE_CARE_LABORATORY_RESULT_UNROUTED");
        super.capture(patient, output);
    }
}
