/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientStatus;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hibernate-based implementation for ProgramClientStatusDAO, recording changes in client program admission status.
 */
@Transactional
public class ProgramClientStatusDAOImpl extends AbstractJpaDao implements ProgramClientStatusDAO {
    // Handles core business logic and state for ProgramClientStatusDAOImpl operations to ensure consistent behavior.

    private Logger log = MiscUtils.getLogger();

    public List<ProgramClientStatus> getProgramClientStatuses(Integer programId) {
        String sSQL = "from ProgramClientStatus pcs where pcs.programId=?1";
        return (List<ProgramClientStatus>) JpqlQueryHelper.find(entityManager(), sSQL, programId);
    }

    public void saveProgramClientStatus(ProgramClientStatus status) {
        if (status.getId() == null) {
            entityManager().persist(status);
        } else {
            entityManager().merge(status);
        }
    }

    public ProgramClientStatus getProgramClientStatus(String id) {
        if (id == null || Integer.valueOf(id) < 0) {
            throw new IllegalArgumentException();
        }

        ProgramClientStatus pcs = null;
        pcs = entityManager().find(ProgramClientStatus.class, Integer.valueOf(id));
        return pcs;
    }

    public void deleteProgramClientStatus(String id) {
        entityManager().remove(getProgramClientStatus(id));
    }

    public boolean clientStatusNameExists(Integer programId, String statusName) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusName == null || statusName.length() <= 0) {
            throw new IllegalArgumentException();
        }

        List<?> results = JpqlQueryHelper.find(entityManager(),
                "from ProgramClientStatus pt where pt.programId = ?1 and pt.name = ?2",
                programId, statusName);

        if (log.isDebugEnabled()) {
            log.debug("clientStatusNameExists: programId = " + programId + ", statusName = " + statusName + ", result = " + !results.isEmpty());
        }
        return !results.isEmpty();
    }

    public List<Admission> getAllClientsInStatus(Integer programId, Integer statusId) {
        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusId == null || statusId <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Admission a where a.programId = ?1 and a.teamId = ?2 and a.admissionStatus='current'";
        List<Admission> results = (List<Admission>) JpqlQueryHelper.find(entityManager(), sSQL, programId, statusId);

        if (log.isDebugEnabled()) {
            log.debug("getAllClientsInStatus: programId= " + programId + ",statusId=" + statusId + ",# results=" + results.size());
        }

        return results;
    }
}
