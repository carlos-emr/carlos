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


package io.github.carlos_emr.carlos.prevention.reports;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Utility class for checking patient rostering status at a specific point in time.
 *
 * <p>Used by prevention reports to determine whether a patient was rostered (enrolled)
 * with a specific provider or any provider on a given date. Checks both current
 * demographic records and archived demographic history.</p>
 *
 * @since 2001-2002
 * @see io.github.carlos_emr.carlos.commn.model.Demographic
 * @see io.github.carlos_emr.carlos.commn.model.DemographicArchive
 */
public final class PreventionReportUtil {
    private static Logger logger = MiscUtils.getLogger();

    public static DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    public static DemographicArchiveDao demographicArchiveDao = (DemographicArchiveDao) SpringUtils.getBean(DemographicArchiveDao.class);

    /**
     * Checks whether a patient was rostered to any provider on the given date.
     * Examines both the current demographic record and archived records.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param demographicId Integer the patient's demographic number
     * @param onThisDate Date the date to check rostering status for
     * @return boolean {@code true} if the patient was rostered on that date
     */
    public static boolean wasRostered(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate) {
        logger.debug("Checking rosterd:" + demographicId);
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicId);

        if (rosteredDuringThisTimeDemographic(onThisDate, demographic.getRosterDate(), demographic.getRosterTerminationDate()))
            return (true);

        List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNo(demographicId);
        for (DemographicArchive archive : archives) {
            if (rosteredDuringThisTimeDemographicArchive(onThisDate, archive.getRosterDate(), archive.getRosterTerminationDate()))
                return (true);
        }

        return (false);
    }


    /**
     * Checks whether a patient was enrolled (rostered) to a specific provider on the given date.
     * Uses the {@code rosterEnrolledTo} field for matching.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param demographicId Integer the patient's demographic number
     * @param onThisDate Date the date to check enrollment for
     * @param providerNo String the provider number to check enrollment against
     * @return boolean {@code true} if the patient was enrolled to the provider
     */
    public static boolean wasEnrolledToThisProvider(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate, String providerNo) {
        logger.debug("Checking rosterd:" + demographicId + " for this date " + onThisDate + " for this providerNo " + providerNo);
        if (providerNo == null) {
            return false;
        }


        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicId);

        if (rosteredDuringThisTimeDemographic(onThisDate, demographic.getRosterDate(), demographic.getRosterTerminationDate()) && providerNo.equals(demographic.getRosterEnrolledTo()))
            return (true);

        List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNo(demographicId);
        for (DemographicArchive archive : archives) {
            if (rosteredDuringThisTimeDemographicArchive(onThisDate, archive.getRosterDate(), archive.getRosterTerminationDate()) && providerNo.equals(demographic.getRosterEnrolledTo()))
                return (true);
        }

        return (false);
    }

    /**
     * Checks whether a patient was rostered to a specific provider on the given date.
     * Uses the primary {@code providerNo} field for matching.
     *
     * @param loggedInInfo LoggedInInfo the logged-in session context
     * @param demographicId Integer the patient's demographic number
     * @param onThisDate Date the date to check rostering for
     * @param providerNo String the provider number to check rostering against
     * @return boolean {@code true} if the patient was rostered to the provider
     */
    public static boolean wasRosteredToThisProvider(LoggedInInfo loggedInInfo, Integer demographicId, Date onThisDate, String providerNo) {
        logger.debug("Checking rosterd:" + demographicId + " for this date " + onThisDate + " for this providerNo " + providerNo);
        if (providerNo == null) {
            return false;
        }


        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicId);

        if (rosteredDuringThisTimeDemographic(onThisDate, demographic.getRosterDate(), demographic.getRosterTerminationDate()) && providerNo.equals(demographic.getProviderNo()))
            return (true);

        List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNo(demographicId);
        for (DemographicArchive archive : archives) {
            if (rosteredDuringThisTimeDemographicArchive(onThisDate, archive.getRosterDate(), archive.getRosterTerminationDate()) && providerNo.equals(demographic.getProviderNo()))
                return (true);
        }

        return (false);
    }

    private static boolean rosteredDuringThisTimeDemographic(Date onThisDate, Date rosterStart, Date rosterEnd) {

        if (rosterStart != null) {
            if (rosterStart.before(onThisDate)) {
                if (rosterEnd == null || rosterEnd.after(onThisDate)) {
                    logger.debug("true:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
                    return (true);
                }
            }
        }

        logger.debug("false:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
        return (false);
    }

    private static boolean rosteredDuringThisTimeDemographicArchive(Date onThisDate, Date rosterStart, Date rosterEnd) {
        // algorithm for demographic archive must only look at archiv erecords with end dates as the archive is populated upon every change not just people being unrostered.
        if (rosterStart != null && rosterEnd != null) {
            if (rosterStart.before(onThisDate) && rosterEnd.after(onThisDate)) {
                logger.debug("true:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
                return (true);
            }
        }

        logger.debug("false:" + onThisDate + ", " + rosterStart + ", " + rosterEnd);
        return (false);
    }
}
