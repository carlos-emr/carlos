/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.appt;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.RScheduleDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.RSchedule;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Data access implementation for appointment operations including deletion,
 * location resolution from schedules, and previous appointment date lookup.
 *
 * <p>Uses Spring-managed DAO beans to interact with appointment, schedule date, and
 * recurring schedule tables. Archives appointments before deletion for audit compliance.</p>
 *
 * @since 2026-03-17
 */
public class JdbcApptImpl {
    private static final Logger _logger = MiscUtils.getLogger();
    AppointmentArchiveDao appointmentArchiveDao = (AppointmentArchiveDao) SpringUtils.getBean(AppointmentArchiveDao.class);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);

    /**
     * Deletes an appointment by number after archiving it for audit purposes.
     *
     * @param apptNo String the appointment number to delete
     * @return boolean {@code true} if the appointment was successfully deleted
     */
    public boolean deleteAppt(String apptNo) {
        Appointment appt = appointmentDao.find(Integer.parseInt(apptNo));
        appointmentArchiveDao.archiveAppointment(appt);
        int retval = 0;
        if (appt != null) {
            appointmentDao.remove(appt.getId());
            retval = 1;
        }
        if (retval == 1) {
            _logger.error("deleteAppt(id=" + apptNo + ")");
        }
        return (retval == 1);
    }

    /**
     * Resolves the appointment location for a provider on a given date by checking
     * schedule date entries and recurring schedule configuration.
     *
     * @param apptDate String the appointment date in {@code yyyy-MM-dd} format
     * @param provider_no String the provider number
     * @return String the resolved location, or an empty string if not found
     */
    public String getLocationFromSchedule(String apptDate, String provider_no) {
        String retval = getLocationFromSpec(apptDate, provider_no, "c");
        if (!"".equals(retval)) {
            return retval;
        }

        retval = getLocationFromSpec(apptDate, provider_no, "b");

        if (!"".equals(retval)) {
            return retval;
        }

        RScheduleDao dao = SpringUtils.getBean(RScheduleDao.class);
        for (RSchedule r : dao.findByProviderNoAndDates(provider_no, ConversionUtils.fromDateString(apptDate))) {
            retval = r.getAvailHour();
        }

        // get weekday number
        String[] temp = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        String strWeekday = UtilDateUtilities.DateToString(UtilDateUtilities.getDateFromString(apptDate, "yyyy-MM-dd"),
                "EEE");
        int n = 0;
        for (int i = 0; i < temp.length; i++) {
            if (temp[i].equalsIgnoreCase(strWeekday)) {
                n = i;
                break;
            }
        }

        retval = SxmlMisc.getXmlContent(retval, "A" + n);
        retval = retval == null ? "" : retval;
        return retval;
    }

    // priority = c, reason = location
    private String getLocationFromSpec(String apptDate, String provider_no, String priority) {
        String retval = "";

        ScheduleDateDao dao = SpringUtils.getBean(ScheduleDateDao.class);
        for (ScheduleDate s : dao.findByProviderStartDateAndPriority(provider_no, ConversionUtils.fromDateString(apptDate), priority)) {
            retval = s.getReason();
        }

        retval = retval == null ? "" : retval;
        return retval;
    }

    /**
     * Finds the previous appointment date before the given service date.
     *
     * @param thisServiceDate String the reference date in {@code yyyy-MM-dd} format
     * @return String the previous appointment date, or an empty string if none found
     */
    public String getPrevApptDate(String thisServiceDate) {
        String retval = "";

        OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);
        Appointment a = dao.findByDate(ConversionUtils.fromDateString(thisServiceDate));

        if (a != null) {
            retval = ConversionUtils.toDateString(a.getAppointmentDate());
        }

        return retval;
    }

}
