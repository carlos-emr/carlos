/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import org.springframework.stereotype.Repository;

@Repository
public class CaseManagementTmpSaveDaoImpl extends AbstractDaoImpl<CaseManagementTmpSave> implements CaseManagementTmpSaveDao {

    private static final String NOTE_TAG_REGEXP = "^\\[[[:digit:]]{2}-[[:alpha:]]{3}-[[:digit:]]{4} \\.\\: [^]]*\\][[:space:]]+$";

    public CaseManagementTmpSaveDaoImpl() {
        super(CaseManagementTmpSave.class);
    }

    @Override
    public void remove(String providerNo, Integer demographicNo, Integer programId) {
        Query query = entityManager.createQuery("SELECT x FROM CaseManagementTmpSave x WHERE x.providerNo = ?1 and x.demographicNo=?2 and x.programId = ?3");
        query.setParameter(1, providerNo);
        query.setParameter(2, demographicNo);
        query.setParameter(3, programId);

        @SuppressWarnings("unchecked")
        List<CaseManagementTmpSave> results = query.getResultList();

        for (CaseManagementTmpSave result : results) {
            remove(result);
        }
    }

    @Override
    public CaseManagementTmpSave find(String providerNo, Integer demographicNo, Integer programId) {
        Query query = entityManager.createQuery("SELECT x FROM CaseManagementTmpSave x WHERE x.providerNo = ?1 and x.demographicNo=?2 and x.programId = ?3 order by x.updateDate DESC");
        query.setParameter(1, providerNo);
        query.setParameter(2, demographicNo);
        query.setParameter(3, programId);

        return this.getSingleResultOrNull(query);
    }

    @Override
    public CaseManagementTmpSave find(String providerNo, Integer demographicNo, Integer programId, Date date) {
        Query query = entityManager.createQuery("SELECT x FROM CaseManagementTmpSave x WHERE x.providerNo = ?1 and x.demographicNo=?2 and x.programId = ?3 and x.updateDate >= ?4 order by x.updateDate DESC");
        query.setParameter(1, providerNo);
        query.setParameter(2, demographicNo);
        query.setParameter(3, programId);
        query.setParameter(4, date);

        return this.getSingleResultOrNull(query);
    }

    @Override
    public boolean noteHasContent(Integer id) {
        Query query = entityManager.createNativeQuery("SELECT * FROM casemgmt_tmpsave x WHERE x.id = ?1 and x.note  NOT REGEXP ?2 order by x.update_date DESC", CaseManagementTmpSave.class);

        query.setParameter(1, id);
        query.setParameter(2, NOTE_TAG_REGEXP);

        return (this.getSingleResultOrNull(query) != null);
    }
}
