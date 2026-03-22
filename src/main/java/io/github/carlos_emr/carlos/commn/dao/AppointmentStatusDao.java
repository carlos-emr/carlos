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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;

/**
 * DAO interface for managing appointment status definitions.
 * <p>
 * Provides operations to retrieve, modify, and manage the appointment status codes
 * (e.g., scheduled, arrived, cancelled) used in the CARLOS EMR scheduling module.
 * Each status has an associated color for display purposes.
 *
 * @since 2001
 */
public interface AppointmentStatusDao extends AbstractDao<AppointmentStatus> {

    /**
     * Retrieves all appointment status definitions.
     *
     * @return List of all {@link AppointmentStatus} records
     */
    public List<AppointmentStatus> findAll();

    /**
     * Retrieves all active appointment status definitions.
     *
     * @return List of active {@link AppointmentStatus} records
     */
    public List<AppointmentStatus> findActive();

    /**
     * Finds an appointment status by its status code.
     *
     * @param status String the status code to look up
     * @return the matching {@link AppointmentStatus}, or {@code null} if not found
     */
    public AppointmentStatus findByStatus(String status);

    /**
     * Modifies the description and color of an existing appointment status.
     *
     * @param ID       int the appointment status identifier
     * @param strDesc  String the new description
     * @param strColor String the new color code for display
     */
    public void modifyStatus(int ID, String strDesc, String strColor);

    /**
     * Changes the active/inactive state of an appointment status.
     *
     * @param ID      int the appointment status identifier
     * @param iActive int the new active state (1 for active, 0 for inactive)
     */
    public void changeStatus(int ID, int iActive);

    /**
     * Checks how many appointments are currently using each of the given status codes.
     *
     * @param allStatus List of {@link AppointmentStatus} records to check
     * @return int the count of appointments using any of the specified statuses
     */
    public int checkStatusUsuage(List<AppointmentStatus> allStatus);
}
