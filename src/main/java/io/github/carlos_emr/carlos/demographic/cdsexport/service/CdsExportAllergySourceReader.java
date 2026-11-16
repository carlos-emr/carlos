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

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Exact physical allergy rows, including archived entries and nullable flags. Partial-date
 * metadata, drug lookup definitions and retained import fields remain separate required
 * clinical inputs; this mechanical capture alone does not establish allergy completeness.
 */
@Service
public class CdsExportAllergySourceReader extends CdsExportPatientTableSourceReader {
    public CdsExportAllergySourceReader(@Qualifier("allergyDao") AllergyDao source) {
        super(source, CdsExportPatientTableReadSupport.Plan.ALLERGIES, Set.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS));
    }
}
