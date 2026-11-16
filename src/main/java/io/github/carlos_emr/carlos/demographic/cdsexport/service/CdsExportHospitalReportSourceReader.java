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

import io.github.carlos_emr.carlos.commn.dao.CdsExportHospitalReportReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The Hospital Report Manager reports linked to the patient ({@code HRMDocument}, every column).
 * Each becomes one CDS 5.2 Reports entry ({@link CdsExportHospitalReportOccurrenceService}).
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportHospitalReportSourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportHospitalReportSourceReader(CdsExportHospitalReportReadDao source) {
        super(hospitalReports(source, Plan.HRM_REPORTS), Plan.HRM_REPORTS, Set.of(CdsCategory.REPORTS));
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportHospitalReportSources.REQUIRED;
    }
}
