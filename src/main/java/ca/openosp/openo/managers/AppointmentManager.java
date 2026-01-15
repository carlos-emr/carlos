//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.managers;

import java.util.List;

import ca.openosp.openo.commn.model.Appointment;
import ca.openosp.openo.commn.model.AppointmentStatus;
import ca.openosp.openo.commn.model.LookupListItem;
import ca.openosp.openo.utility.LoggedInInfo;

public interface AppointmentManager {

    public List<Appointment> getAppointmentHistoryWithoutDeleted(LoggedInInfo loggedInInfo, Integer demographicNo, Integer offset, Integer limit);

    public List<Object> getAppointmentHistoryWithDeleted(LoggedInInfo loggedInInfo, Integer demographicNo, Integer offset, Integer limit);

    /**
     * Adds an appointment to the system.
     *
     * @param loggedInInfo logged in provider information
     * @param appointment appointment data to add
     */
    public void addAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    public void updateAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    public void deleteAppointment(LoggedInInfo loggedInInfo, int apptNo);

    public Appointment getAppointment(LoggedInInfo loggedInInfo, int apptNo);

    public Appointment updateAppointmentStatus(LoggedInInfo loggedInInfo, int apptNo, String status);


    public Appointment updateAppointmentType(LoggedInInfo loggedInInfo, int apptNo, String type);

    public Appointment updateAppointmentUrgency(LoggedInInfo loggedInInfo, int apptNo, String urgency);

    public List<AppointmentStatus> getAppointmentStatuses();

    public List<LookupListItem> getReasons();

    public List<Appointment> findMonthlyAppointments(LoggedInInfo loggedInInfo, String providerNo, int year, int month);

    public String getNextAppointmentDate(Integer demographicNo);
}
