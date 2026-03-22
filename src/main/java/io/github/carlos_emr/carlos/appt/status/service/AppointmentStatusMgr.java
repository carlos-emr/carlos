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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.appt.status.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;

/**
 * Service interface for managing appointment status configurations.
 *
 * <p>Provides CRUD operations for appointment statuses used to track the workflow
 * state of appointments (e.g., To Do, Here, Picked, Billed, Cancelled). Supports
 * activating/deactivating statuses, modifying descriptions and colors, checking
 * usage, and resetting to default values.</p>
 *
 * @since 2026-03-17
 */
public interface AppointmentStatusMgr {

    /**
     * Returns all appointment statuses including inactive ones.
     *
     * @return List&lt;AppointmentStatus&gt; all appointment statuses
     */
    public List<AppointmentStatus> getAllStatus();

    /**
     * Returns only the active appointment statuses.
     *
     * @return List&lt;AppointmentStatus&gt; the active statuses
     */
    public List<AppointmentStatus> getAllActiveStatus();

    /**
     * Returns the appointment status with the given ID.
     *
     * @param ID int the status ID
     * @return AppointmentStatus the status record
     */
    public AppointmentStatus getStatus(int ID);

    /**
     * Activates or deactivates an appointment status.
     *
     * @param ID int the status ID
     * @param iActive int 1 for active, 0 for inactive
     */
    public void changeStatus(int ID, int iActive);

    /**
     * Modifies the description and color of an appointment status.
     *
     * @param ID int the status ID
     * @param strDesc String the new description
     * @param strColor String the new hex color code
     */
    public void modifyStatus(int ID, String strDesc, String strColor);

    /**
     * Checks which statuses are currently in use by appointments.
     *
     * @param allStatus List&lt;AppointmentStatus&gt; the statuses to check
     * @return int the ID of the last status in use, or 0 if none
     */
    public int checkStatusUsuage(List<AppointmentStatus> allStatus);

    /**
     * Resets all appointment statuses to their default values.
     */
    public void reset();
}
