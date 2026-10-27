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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportProgramDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProgramContextDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative context remains readable while patient data and Program formulas are closed. */
@Service
@Transactional(readOnly = true, timeout = 30)
public class CdsProgramContextService {
    private final CdsImportProgramDao programs;
    public CdsProgramContextService(CdsImportProgramDao programs) { this.programs = programs; }

    public CdsProgramContextDto metadata(int id) { return programs.metadata(id); }
    public boolean member(String provider, int program) { return programs.member(provider, program); }

    /** Preserves EctProgram's active-member default, with a stable oldest-membership fallback. */
    public Integer defaultProgramId(String provider) {
        Integer preferred = programs.preferred(provider);
        if (preferred != null && preferred > 0) {
            var context = programs.metadata(preferred);
            if (context != null && "active".equals(context.status()) && programs.member(provider, preferred)) return preferred;
        }
        long after = 0;
        while (true) {
            var page = programs.memberships(provider, after);
            if (page.size() > 100) throw new CdsImportException("PROGRAM_CONTEXT_PAGE_INVALID");
            if (page.isEmpty()) return null;
            for (var membership : page) {
                if (membership.membershipId() <= after || membership.programId() < 1) throw new CdsImportException("PROGRAM_CONTEXT_PAGE_INVALID");
                after = membership.membershipId();
                if ("active".equals(membership.status())) return membership.programId();
            }
        }
    }
}
