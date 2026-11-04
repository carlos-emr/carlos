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

public interface AppointmentStatusDao extends AbstractDao<AppointmentStatus> {

    public List<AppointmentStatus> findAll();

    public List<AppointmentStatus> findActive();

    public AppointmentStatus findByStatus(String status);


    public void changeStatus(int ID, int iActive);

    /**
     * Updates the style of one status in a single transaction and evicts the status cache.
     * A {@code null} argument leaves that field as it is.
     *
     * @param ID int the appointment_status id
     * @param description String the new description, or null to keep it
     * @param color String the new {@code #rrggbb} colour, or null to keep it
     * @param icon String the new icon file name under {@code /images}, or null to keep it
     * @return boolean true if the status exists and was updated, false if there is no such status
     * @since 2026-11-04
     */
    public boolean modifyStyle(int ID, String description, String color, String icon);

    /**
     * Finds the first inactive status that still has associated appointments.
     *
     * @param allStatus statuses to check, in the order referenced by the returned index
     * @return the index of the first inactive status in use, or {@code -1} when none are in use
     */
    public int checkStatusUsuage(List<AppointmentStatus> allStatus);
}
