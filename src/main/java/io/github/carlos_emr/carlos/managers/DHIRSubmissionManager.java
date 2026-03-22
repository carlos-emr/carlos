/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DHIRSubmissionLogDao;
import io.github.carlos_emr.carlos.commn.model.DHIRSubmissionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing Digital Health Immunization Repository (DHIR) submissions
 * in the CARLOS EMR system.
 *
 * <p>Tracks the lifecycle of immunization data submissions to the Ontario DHIR
 * system, including creation, status updates, and retrieval of submission logs
 * by prevention record identifier.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.dao.DHIRSubmissionLogDao
 * @see io.github.carlos_emr.carlos.commn.model.DHIRSubmissionLog
 * @since 2026-03-17
 */
@Service
public class DHIRSubmissionManager {

    @Autowired
    DHIRSubmissionLogDao dhirSubmissionDao;

    /**
     * Persists a new DHIR submission log record.
     *
     * @param submission DHIRSubmissionLog the submission record to save
     */
    public void save(DHIRSubmissionLog submission) {
        dhirSubmissionDao.persist(submission);
    }

    /**
     * Updates an existing DHIR submission log record.
     *
     * @param submission DHIRSubmissionLog the submission record to update
     */
    public void update(DHIRSubmissionLog submission) {
        dhirSubmissionDao.merge(submission);
    }

    /**
     * Finds the most recent pending submission for a prevention record.
     *
     * @param preventionId Integer the prevention record identifier
     * @return DHIRSubmissionLog the latest pending submission, or null if none
     */
    public DHIRSubmissionLog findLatestPendingByPreventionId(Integer preventionId) {
        return dhirSubmissionDao.findLatestPendingByPreventionId(preventionId);
    }

    /**
     * Retrieves all submission log records for a prevention record.
     *
     * @param preventionId Integer the prevention record identifier
     * @return List of DHIRSubmissionLog records for the prevention
     */
    public List<DHIRSubmissionLog> findByPreventionId(Integer preventionId) {
        return dhirSubmissionDao.findByPreventionId(preventionId);
    }

}
