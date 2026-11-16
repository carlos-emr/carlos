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

/**
 * Start-date precision rows ({@code partial_date} table 3) of the patient's Disease Registry
 * entries, under their own logical name so they can never be counted as, or matched to, an
 * allergy, medication or prevention precision row of the same physical table. The registry shows
 * a start date at its stored year or year-month precision, and the export keeps that precision.
 */
@Service
public class CdsExportDiseaseRegistryPrecisionSourceReader
        extends CdsExportPatientTableSourceReader {
    private final PartialDateDao source;

    public CdsExportDiseaseRegistryPrecisionSourceReader(PartialDateDao source) {
        super(
                source,
                CdsExportPatientTableReadSupport.Plan.DISEASE_REGISTRY_PRECISION,
                Set.of(CdsCategory.PROBLEM_LIST));
        this.source = source;
    }

    @Override
    public String table() {
        return CdsExportProblemRegistrySources.PRECISION;
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportProblemRegistrySources.TABLES;
    }

    @Override
    public long count(int patient) {
        return source.countCdsExportDiseaseRegistryRows(patient);
    }

    @Override
    protected java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> page(
            int patient, Long after, int limit) {
        return source.findCdsExportDiseaseRegistryRowsAfter(patient, after, limit);
    }

    @Override
    protected byte[] chunk(int patient, long row, String column, long offset, int length) {
        return source.readCdsExportDiseaseRegistryChunk(patient, row, column, offset, length);
    }
}
