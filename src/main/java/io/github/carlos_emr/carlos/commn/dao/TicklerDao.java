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

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;

/**
 * DAO interface for tickler (task reminder) operations.
 *
 * @since 2001
 */

public interface TicklerDao extends AbstractDao<Tickler> {

    /**
     * Finds a Tickler by its ID.
     */
    public Tickler find(Integer id);

    /**
     * Finds active ticklers for patients based on demographic numbers and a message.
     */
    public List<Tickler> findActiveByMessageForPatients(List<Integer> demographicNos, String remString);

    /**
     * Finds active ticklers by demographic number and message.
     */
    public List<Tickler> findActiveByDemographicNoAndMessage(Integer demoNo, String message);

    public List<Tickler> findActiveByDemographicNo(Integer demoNo);

    public List<Tickler> findByTicklerNoDemo(Integer ticklerNo, Integer demoNo);

    public List<Tickler> findByTicklerNoAssignedTo(Integer ticklerNo, String assignedTo, Integer demoNo);

    /**
     * Retrieves a list of Tickler objects based on demographic ID, assigned task, and message.
     */
    public List<Tickler> findByDemographicIdTaskAssignedToAndMessage(Integer demographicNo, String taskAssignedTo,
                                                                     String message);

    /**
     * Searches for ticklers based on demographic number and status within a date range.
     */
    public List<Tickler> search_tickler_bydemo(Integer demographicNo, String status, Date beginDate, Date endDate);

    /**
     * Searches for ticklers based on demographic number and end date.
     */
    public List<Tickler> search_tickler(Integer demographicNo, Date endDate);

    public List<Tickler> listTicklers(Integer demographicNo, Date beginDate, Date endDate);

    /**
     * Returns the count of active ticklers for the specified provider.
     */
    public int getActiveTicklerCount(String providerNo);

    /**
     * Returns the count of active ticklers for a given demographic number.
     */
    public int getActiveTicklerByDemoCount(Integer demographicNo);

    public List<Tickler> getTicklers(CustomFilter filter, int offset, int limit);

    /**
     * Retrieves a list of tickler DTOs based on the specified filter criteria.
     */
    public List<Tickler> getTicklers(CustomFilter filter);

    /**
     * Returns the count of tickler DTOs matching the specified filter criteria.
     */
    public int getNumTicklers(CustomFilter filter);

    /**
     * Returns the count of tickler DTOs matching all filter criteria including
     * the searchTerm field. Used by the DataTables server-side endpoint to
     * return accurate {@code recordsFiltered} counts when the search box is used.
     *
     * @param filter CustomFilter the filter criteria, may include searchTerm
     * @return int count of ticklers matching all criteria
     * @since 2026-03-15
     */
    public int getNumFilteredTicklerDTOs(CustomFilter filter);

    /**
     * Returns paginated tickler data as lightweight DTOs using JPQL constructor
     * expression projection. Batch loads comments and links to avoid N+1 queries.
     *
     * @param filter CustomFilter the filter criteria
     * @param offset int the starting position for pagination
     * @param limit int the maximum number of results, or &lt;= 0 for no limit
     * @return List of TicklerListDTO matching the filter criteria
     * @since 2026-02-27
     */
    List<TicklerListDTO> getTicklerDTOs(CustomFilter filter, int offset, int limit);

    /**
     * Returns all tickler data as lightweight DTOs, limited to MAX_LIST_RETURN_SIZE.
     *
     * @param filter CustomFilter the filter criteria
     * @return List of TicklerListDTO matching the filter criteria
     * @since 2026-02-27
     */
    List<TicklerListDTO> getTicklerDTOs(CustomFilter filter);
}
