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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Every physical prescription row, including archived, hidden, historical and renewed rows.
 * SQL display projections of floating dose columns remain explicitly unqualified witnesses;
 * neither this capture nor an equal legacy row digest establishes an exact dose or currentness.
 * Partial dates, prescribers, script/pharmacy relations and retained source are independent inputs.
 */
@Service
public class CdsExportMedicationSourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportMedicationSourceReader(@Qualifier("drugDao") DrugDao source) {
        super(source, CdsExportPatientTableReadSupport.Plan.DRUGS, Set.of(CdsCategory.MEDICATIONS_AND_TREATMENTS));
    }
}
