/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.services;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tickler-specific service for accessing patient demographic information.
 *
 * <p>This transactional service provides demographic data retrieval operations
 * used by the tickler (clinical reminder) subsystem. It wraps {@link DemographicDao}
 * to supply patient identification and program enrollment data needed when
 * creating or processing tickler reminders.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.dao.DemographicDao
 * @see io.github.carlos_emr.carlos.commn.model.Demographic
 * @since 2026-03-17
 */
@Transactional
public class DemographicManagerTickler {

    private DemographicDao demographicDao = null;

    /**
     * Sets the demographic data access object via Spring dependency injection.
     *
     * @param demographicDao DemographicDao the DAO instance for demographic database operations
     */
    public void setDemographicDao(DemographicDao demographicDao) {
        this.demographicDao = demographicDao;
    }

    /**
     * Retrieves a patient demographic record by demographic number.
     *
     * @param demographic_no String the unique patient demographic identifier
     * @return Demographic the patient record, or null if not found
     */
    public Demographic getDemographic(String demographic_no) {
        return demographicDao.getDemographic(demographic_no);
    }

    /**
     * Retrieves all patient demographic records.
     *
     * @return List of all Demographic records in the system
     */
    public List getDemographics() {
        return demographicDao.getDemographics();
    }

    /**
     * Retrieves program identifiers for a patient by demographic number.
     *
     * @param demoNo String the demographic number (parsed to Integer internally)
     * @return List of Integer program IDs associated with the patient
     */
    public List getProgramIdByDemoNo(String demoNo) {
        return demographicDao.getProgramIdByDemoNo(Integer.parseInt(demoNo));
    }

    /**
     * Retrieves program enrollment records for a patient.
     *
     * @param demoNo Integer the demographic number of the patient
     * @return List of program enrollment records for the patient
     */
    public List getDemoProgram(Integer demoNo) {
        return demographicDao.getDemoProgram(demoNo);
    }

}
