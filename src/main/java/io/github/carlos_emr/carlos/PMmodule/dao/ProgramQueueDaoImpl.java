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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

/**
 * Hibernate-based implementation of {@link ProgramQueueDao} for managing
 * {@link ProgramQueue} entities.
 *
 * @since 2005-01-18
 * @see ProgramQueueDao
 */
@Transactional
public class ProgramQueueDaoImpl extends AbstractHibernateDao implements ProgramQueueDao {

    private Logger log = MiscUtils.getLogger();

    @Override
    public ProgramQueue getProgramQueue(Long queueId) {
        if (queueId == null || queueId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = currentSession().get(ProgramQueue.class, queueId);

        if (log.isDebugEnabled()) {
            log.debug("getProgramQueue: queueId=" + queueId + ", found=" + (result != null));
        }

        return result;
    }

    @Override
    public List<ProgramQueue> getProgramQueuesByProgramId(Long programId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = " FROM ProgramQueue q WHERE q.ProgramId=?1 ORDER BY  q.Id  ";
        List results = HqlQueryHelper.find(currentSession(), queryStr, programId);

        if (log.isDebugEnabled()) {
            log.debug("getProgramQueue: programId=" + programId + ", # of results=" + results.size());
        }

        return results;
    }

    @Override
    public List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }

        List results = HqlQueryHelper.find(currentSession(),
                "from ProgramQueue pq where pq.ProgramId = ?1 and pq.Status = 'active' order by pq.ReferralDate",
                Long.valueOf(programId));

        if (log.isDebugEnabled()) {
            log.debug("getActiveProgramQueuesByProgramId: programId=" + programId + ", # of results=" + results.size());
        }

        return results;
    }

    @Override
    public void saveProgramQueue(ProgramQueue programQueue) {
        if (programQueue == null) {
            return;
        }

        if (programQueue.getId() == null) {
            currentSession().persist(programQueue);
        } else {
            currentSession().merge(programQueue);
        }

        if (log.isDebugEnabled()) {
            log.debug("saveProgramQueue: id=" + programQueue.getId());
        }

    }

    @Override
    public ProgramQueue getQueue(Long programId, Long clientId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }
        if (clientId == null) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = null;
        String sSQL = "from ProgramQueue pq where pq.ProgramId = ?1 and pq.ClientId = ?2";
        List results = HqlQueryHelper.find(currentSession(), sSQL, Long.valueOf(programId), Long.valueOf(clientId));

        if (!results.isEmpty()) {
            result = (ProgramQueue) results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getQueue: programId=" + programId + ", clientId=" + clientId + ", found=" + (result != null));
        }

        return result;
    }

    @Override
    public ProgramQueue getActiveProgramQueue(Long programId, Long demographicNo) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (demographicNo == null || demographicNo.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = null;

        String sSQL = "from ProgramQueue pq where pq.ProgramId = ?1 and pq.ClientId = ?2 and pq.Status='active'";
        List results = HqlQueryHelper.find(currentSession(), sSQL, programId, demographicNo);
        if (!results.isEmpty()) {
            result = (ProgramQueue) results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getActiveProgramQueue: programId=" + programId + ", demogaphicNo=" + demographicNo + ", found="
                    + (result != null));
        }

        return result;
    }
}
