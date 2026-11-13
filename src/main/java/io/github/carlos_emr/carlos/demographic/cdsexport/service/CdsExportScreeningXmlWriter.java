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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportScreeningDto;
import java.io.IOException;

/**
 * Writes a native screening as PastHealth content in CDS 5.2 sequence order: ResidualInfo,
 * PastHealthProblemDescriptionOrProcedures, ProcedureDate, Notes. The wire limits are checked
 * here independently of the mapper; text is never clipped to fit them.
 */
public final class CdsExportScreeningXmlWriter {
    public void write(CdsExportScreeningDto value, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        validate(value);
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(
                xml,
                CdsNativeExtensionContract.PREVENTION_SOURCE_CONTEXT,
                CdsNativeExtensionContract.STRING_TYPE,
                value.sourceContext());
        xml.end();
        xml.element("cds", "PastHealthProblemDescriptionOrProcedures", value.description());
        if (value.procedureDate().precision() != CdsExportDateDto.Precision.ABSENT) {
            xml.start("cds", "ProcedureDate");
            CdsExportNativeHistoryXmlWriter.dateContents(xml, value.procedureDate());
            xml.end();
        }
        if (value.notes() != null) xml.element("cds", "Notes", value.notes());
    }

    static void validate(CdsExportScreeningDto value) throws IOException {
        if (value == null
                || value.description().isBlank()
                || !CdsExportNativeFieldAssessments.legal(value.description(), 250)
                // dateFullOrPartial has no clock arm: a native time stays in the residual.
                || value.procedureDate().precision() == CdsExportDateDto.Precision.DATE_TIME
                || value.notes() != null
                        && (value.notes().isEmpty()
                                || !CdsExportNativeFieldAssessments.legal(value.notes(), 65_536))
                || value.sourceContext().isEmpty()
                || !CdsExportNativeFieldAssessments.legal(value.sourceContext(), 1 << 24)
                || value.sourceContext().chars().anyMatch(c -> c == ' ' || c == '\t' || c == '\r' || c == '\n'))
            throw new IOException("CDS_EXPORT_SCREENING_VALUE_INVALID");
    }
}
