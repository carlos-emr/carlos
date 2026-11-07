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

import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Fresh restriction metadata only. This does not replace account/category/patient authorization or grant program access. */
@Service
@Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED)
public class CdsExportPreventionAccessService {
    private final PreventionDao source;
    public CdsExportPreventionAccessService(PreventionDao source){this.source=java.util.Objects.requireNonNull(source); }
    public void requireUnrestricted(int patient){
        if (patient<1)throw new IllegalArgumentException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        if (source.hasCdsExportUnresolvedProgramScope(patient))throw new CdsExportReviewException(CdsExportReviewException.Reason.PREVENTION_PROGRAM_SCOPE);
    }
}
