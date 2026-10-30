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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.ProviderDefaultProgram;
import io.github.carlos_emr.carlos.commn.dto.CdsProgramContextDto;
import io.github.carlos_emr.carlos.commn.dto.CdsProgramMembershipDto;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Scalar administrative projections exclude Program's clinical formulas and eager memberships. */
@Repository
public class CdsImportProgramDao extends AbstractDaoImpl<ProviderDefaultProgram> {
    public CdsImportProgramDao() { super(ProviderDefaultProgram.class); }

    public Integer preferred(String provider) {
        return entityManager.createQuery("select d.programId from ProviderDefaultProgram d "
                + "where d.providerNo=:provider order by d.id", Integer.class)
                .setParameter("provider", provider).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }

    public CdsProgramContextDto metadata(int id) {
        var rows = entityManager.createQuery("select p.id,p.name,p.programStatus,p.facilityId "
                + "from Program p where p.id=:id", Object[].class)
                .setParameter("id", id).setMaxResults(1).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new CdsProgramContextDto((Integer) row[0], (String) row[1], (String) row[2], (Integer) row[3]);
    }

    public boolean member(String provider, int program) {
        return entityManager.createQuery("select count(p.id) from ProgramProvider p "
                + "where p.providerNo=:provider and p.programId=:program", Long.class)
                .setParameter("provider", provider).setParameter("program", (long) program).getSingleResult() > 0;
    }

    public List<CdsProgramMembershipDto> memberships(String provider, long after) {
        return entityManager.createQuery("select pp.id,p.id,p.programStatus from ProgramProvider pp "
                + "join pp.program p where pp.providerNo=:provider and pp.id>:after and p.id>0 order by pp.id", Object[].class)
                .setParameter("provider", provider).setParameter("after", after).setMaxResults(100).getResultList()
                .stream().map(row -> new CdsProgramMembershipDto((Long) row[0], (Integer) row[1], (String) row[2])).toList();
    }
}
