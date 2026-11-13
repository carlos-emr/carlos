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

import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Complete deleted-observation projection. A deleted or superseded measurement version is
 * captured so every field can be accounted, but it is never exported (see
 * CdsExportNativeCareMapper.retired), so it needs no supporting NewCategory carrier.
 */
@Service
public class CdsExportDeletedMeasurementSourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportDeletedMeasurementSourceReader(MeasurementsDeletedDao source) {
        super(
                source,
                CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED,
                Set.of(CdsCategory.CARE_ELEMENTS));
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("measurements", "measurementsExt", "measurementsDeleted", "measurementType");
    }
}
