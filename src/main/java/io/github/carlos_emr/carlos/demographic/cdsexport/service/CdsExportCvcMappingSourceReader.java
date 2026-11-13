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

import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Frozen public CVC vocabulary definitions; these are not patient immunization occurrences or proof of code equivalence. */
@Service
public class CdsExportCvcMappingSourceReader extends CdsExportPatientTableSourceReader {
    private final CVCMappingDao source;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportCvcMappingSourceReader(CVCMappingDao source) {
        this(source, CdsExportPreventionSources.CATEGORIES);
    }

    private CdsExportCvcMappingSourceReader(CVCMappingDao source, Set<CdsCategory> selected) {
        super(source, CdsExportPatientTableReadSupport.Plan.CVC_MAPPING, selected);
        this.source = source;
    }

    @Override
    public Set<CdsCategory> supportedCategories() {
        return CdsExportPreventionSources.CATEGORIES;
    }

    /** Vaccines and screenings share one classification; only the selected categories are authorized. */
    @Override
    public CdsExportNativeSourceReader forRequest(
            io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        return new CdsExportCvcMappingSourceReader(source, CdsExportPreventionSources.selected(command));
    }
}
