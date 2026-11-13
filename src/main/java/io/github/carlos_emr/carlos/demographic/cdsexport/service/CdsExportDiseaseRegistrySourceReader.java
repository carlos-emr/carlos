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
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The patient's Disease Registry entries in every status. A deleted entry is captured so it can
 * be accounted for, never silently left out of the capture.
 */
@Service
public class CdsExportDiseaseRegistrySourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportDiseaseRegistrySourceReader(DxresearchDAO source) {
        super(
                source,
                CdsExportPatientTableReadSupport.Plan.DISEASE_REGISTRY,
                Set.of(CdsCategory.PROBLEM_LIST));
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportProblemRegistrySources.TABLES;
    }
}
