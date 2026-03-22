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

import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;

/**
 * Data access interface for managing {@link ProgramQueue} entities that represent
 * client queue entries for program admission.
 *
 * @since 2005-01-18
 * @see ProgramQueue
 * @see ProgramQueueDaoImpl
 */
public interface ProgramQueueDao {

    /**
     * Retrieves a program queue entry by its ID.
     *
     * @param queueId Long the queue entry ID
     * @return ProgramQueue the queue entry, or {@code null} if not found
     * @throws IllegalArgumentException if queueId is {@code null} or not positive
     */
    public ProgramQueue getProgramQueue(Long queueId);

    /**
     * Retrieves all queue entries for a specific program, ordered by ID.
     *
     * @param programId Long the program ID
     * @return List&lt;ProgramQueue&gt; queue entries for the program
     * @throws IllegalArgumentException if programId is {@code null}
     */
    public List<ProgramQueue> getProgramQueuesByProgramId(Long programId);

    /**
     * Retrieves active queue entries for a program, ordered by referral date.
     *
     * @param programId Long the program ID
     * @return List&lt;ProgramQueue&gt; active queue entries
     * @throws IllegalArgumentException if programId is {@code null}
     */
    public List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId);

    /**
     * Saves or updates a program queue entry.
     *
     * @param programQueue ProgramQueue the queue entry to persist
     */
    public void saveProgramQueue(ProgramQueue programQueue);

    /**
     * Retrieves a queue entry by program and client IDs.
     *
     * @param programId Long the program ID
     * @param clientId Long the client demographic ID
     * @return ProgramQueue the queue entry, or {@code null} if not found
     * @throws IllegalArgumentException if either parameter is {@code null}
     */
    public ProgramQueue getQueue(Long programId, Long clientId);

    /**
     * Retrieves an active queue entry for a client in a specific program.
     *
     * @param programId Long the program ID
     * @param demographicNo Long the demographic number of the client
     * @return ProgramQueue the active queue entry, or {@code null} if not found
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ProgramQueue getActiveProgramQueue(Long programId, Long demographicNo);
}
