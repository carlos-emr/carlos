//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.casemgmt.dao;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.casemgmt.model.CaseManagementCPP;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for managing Cumulative Patient Profile (CPP) records.
 * <p>
 * This DAO provides CRUD operations for the CPP, which stores comprehensive patient information
 * including medical history, family history, social history, ongoing concerns, reminders, and
 * other clinical data that follows the patient across encounters.
 * </p>
 * <p>
 * Migrated from HibernateDaoSupport to direct SessionFactory injection for Spring 6 compatibility.
 * </p>
 *
 * @since 15.0
 */
/*
 * Updated by Eugene Petruhin on 09 jan 2009 while fixing #2482832 & #2494061
 */
public class CaseManagementCPPDAOImpl implements CaseManagementCPPDAO {

    private Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves the most recent Cumulative Patient Profile for a given demographic.
     * <p>
     * If multiple CPP records exist for the demographic, returns the one with the
     * most recent update date.
     * </p>
     *
     * @param demographic_no the demographic number (patient ID) to retrieve CPP for
     * @return the most recent CaseManagementCPP for the demographic, or null if none exists
     */
    @Override
    @SuppressWarnings("unchecked")
    public CaseManagementCPP getCPP(String demographic_no) {
        List<CaseManagementCPP> results = getSession()
                .createQuery("from CaseManagementCPP cpp where cpp.demographic_no = :demographicNo order by update_date desc")
                .setParameter("demographicNo", demographic_no)
                .list();
        return (results.size() != 0) ? results.get(0) : null;
    }

    /**
     * Saves or updates a Cumulative Patient Profile record.
     * <p>
     * This method ensures that all text fields are non-null by converting null values to empty strings,
     * sets the update date to the current timestamp, and persists the CPP to the database.
     * </p>
     *
     * @param cpp the CaseManagementCPP object to save or update
     */
    @Override
    public void saveCPP(CaseManagementCPP cpp) {

        String fhist = cpp.getFamilyHistory() == null ? "" : cpp.getFamilyHistory();
        String mhist = cpp.getMedicalHistory() == null ? "" : cpp.getMedicalHistory();
        String ongoing = cpp.getOngoingConcerns() == null ? "" : cpp.getOngoingConcerns();
        String rem = cpp.getReminders() == null ? "" : cpp.getReminders();
        String shist = cpp.getSocialHistory() == null ? "" : cpp.getSocialHistory();
        String ofnum = cpp.getOtherFileNumber() == null ? "" : cpp.getOtherFileNumber();
        String ossystem = cpp.getOtherSupportSystems() == null ? "" : cpp.getOtherSupportSystems();
        String pm = cpp.getPastMedications() == null ? "" : cpp.getPastMedications();

        cpp.setFamilyHistory(fhist);
        cpp.setMedicalHistory(mhist);
        cpp.setOngoingConcerns(ongoing);
        cpp.setReminders(rem);
        cpp.setSocialHistory(shist);
        cpp.setUpdate_date(new Date());
        cpp.setOtherFileNumber(ofnum);
        cpp.setOtherSupportSystems(ossystem);
        cpp.setPastMedications(pm);

        if (log.isDebugEnabled()) {
            log.debug("Saving or updating a CPP: " + cpp);
        }

        getSession().saveOrUpdate(cpp);

    }
}
