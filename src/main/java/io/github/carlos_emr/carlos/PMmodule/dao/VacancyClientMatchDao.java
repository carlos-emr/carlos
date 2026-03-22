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
package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.VacancyClientMatch;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing {@link VacancyClientMatch} entities that
 * track the results of client-vacancy matching in the waitlist subsystem.
 *
 * @since 2001-09-17
 * @see VacancyClientMatch
 * @see VacancyClientMatchDaoImpl
 */
public interface VacancyClientMatchDao extends AbstractDao<VacancyClientMatch> {

    /**
     * Finds match records by client and vacancy IDs.
     *
     * @param clientId int the client demographic ID
     * @param vacancyId int the vacancy ID
     * @return List&lt;VacancyClientMatch&gt; matching records
     */
    public List<VacancyClientMatch> findByClientIdAndVacancyId(int clientId, int vacancyId);

    /**
     * Finds all match records for a specific client.
     *
     * @param clientId int the client demographic ID
     * @return List&lt;VacancyClientMatch&gt; match records for the client
     */
    public List<VacancyClientMatch> findByClientId(int clientId);

    /**
     * Finds all match records with a specific status.
     *
     * @param status String the match status to filter by
     * @return List&lt;VacancyClientMatch&gt; match records with the specified status
     */
    public List<VacancyClientMatch> findBystatus(String status);

    /**
     * Updates the status of match records for a client-vacancy combination.
     *
     * @param status String the new status
     * @param clientId int the client demographic ID
     * @param vacancyId int the vacancy ID
     */
    public void updateStatus(String status, int clientId, int vacancyId);

    /**
     * Updates the status and rejection reason for match records of a client-vacancy combination.
     *
     * @param status String the new status
     * @param rejectedReason String the rejection reason
     * @param clientId int the client demographic ID
     * @param vacancyId int the vacancy ID
     */
    public void updateStatusAndRejectedReason(String status, String rejectedReason, int clientId, int vacancyId);

}
