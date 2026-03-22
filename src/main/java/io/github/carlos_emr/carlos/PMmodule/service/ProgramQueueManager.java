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
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ClientReferralDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramQueueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyTemplateDao;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;

/**
 * Service interface for managing program queues within the CARLOS EMR Program Management module.
 *
 * <p>Program queues hold clients who have been referred to a program but not yet admitted.
 * This interface provides operations for queue retrieval, persistence, and rejection
 * processing, including enrichment with vacancy and template information.</p>
 *
 * @see ProgramQueueManagerImpl
 * @see ProgramQueue
 * @since 2005
 */
public interface ProgramQueueManager {

    /**
     * Sets the vacancy data access object.
     *
     * @param vacancyDao VacancyDao the vacancy DAO to inject
     */
    void setVacancyDao(VacancyDao vacancyDao);

    /**
     * Sets the vacancy template data access object.
     *
     * @param vacancyTemplateDao VacancyTemplateDao the vacancy template DAO to inject
     */
    void setVacancyTemplateDao(VacancyTemplateDao vacancyTemplateDao);

    /**
     * Sets the program queue data access object.
     *
     * @param dao ProgramQueueDao the program queue DAO to inject
     */
    void setProgramQueueDao(ProgramQueueDao dao);

    /**
     * Sets the client referral data access object.
     *
     * @param dao ClientReferralDAO the client referral DAO to inject
     */
    void setClientReferralDAO(ClientReferralDAO dao);

    /**
     * Retrieves a program queue entry by its identifier.
     *
     * @param queueId String the queue entry identifier
     * @return ProgramQueue the queue entry
     */
    ProgramQueue getProgramQueue(String queueId);

    /**
     * Retrieves all queue entries for a specific program.
     *
     * @param programId Long the program identifier
     * @return List&lt;ProgramQueue&gt; list of queue entries for the program
     */
    List<ProgramQueue> getProgramQueuesByProgramId(Long programId);

    /**
     * Persists a program queue entry.
     *
     * @param programQueue ProgramQueue the queue entry to save
     */
    void saveProgramQueue(ProgramQueue programQueue);

    /**
     * Retrieves active queue entries for a program, enriched with vacancy and template names.
     *
     * @param programId Long the program identifier
     * @return List&lt;ProgramQueue&gt; list of active queue entries with enriched vacancy data
     */
    List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId);

    /**
     * Retrieves the active queue entry for a specific client in a specific program.
     *
     * @param programId String the program identifier
     * @param demographicNo String the client demographic number
     * @return ProgramQueue the active queue entry, or {@code null} if not queued
     */
    ProgramQueue getActiveProgramQueue(String programId, String demographicNo);

    /**
     * Rejects a client from the program queue and updates the associated referral.
     *
     * @param programId String the program identifier
     * @param clientId String the client demographic number
     * @param notes String rejection notes
     * @param rejectionReason String the reason for rejection
     */
    void rejectQueue(String programId, String clientId, String notes, String rejectionReason);
}
