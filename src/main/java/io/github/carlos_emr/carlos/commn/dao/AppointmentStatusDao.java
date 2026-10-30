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

    /**
     * Returns every appointment status, active or not.
     *
     * <p>Served from the shared {@code appointmentStatuses} cache. Every call returns an unmodifiable list of
     * new detached copies, so changing an element affects only the caller's copy, never another
     * reader or the cache, and writes nothing to the database. Persist changes through
     * {@code merge}/{@code saveEntity}, which evict the cache.</p>
     *
     * @return all statuses, as detached copies
     */
    public List<AppointmentStatus> findAll();

    /**
     * Returns the active appointment statuses ordered by id.
     *
     * <p>Served from the shared {@code appointmentStatuses} cache. Every call returns an unmodifiable list of
     * new detached copies, so changing an element affects only the caller's copy, never another
     * reader or the cache, and writes nothing to the database. Persist changes through
     * {@code merge}/{@code saveEntity}, which evict the cache.</p>
     *
     * @return the active statuses, as detached copies
     */
    public List<AppointmentStatus> findActive();

    /**
     * Finds the status whose code starts with the first character of {@code status}.
     *
     * <p>Served from the shared {@code appointmentStatuses} cache. Every call returns a new
     * detached copy, so changing it affects only the caller's copy and writes nothing.</p>
     *
     * @param status the status code; only its first character is matched
     * @return a detached copy of the matching status, or {@code null} when {@code status} is
     *         null/empty or nothing matches
     */
    public AppointmentStatus findByStatus(String status);

    public void modifyStatus(int ID, String strDesc, String strColor);

    public void changeStatus(int ID, int iActive);

    /**
     * Finds the first inactive status that still has associated appointments.
     *
     * @param allStatus statuses to check, in the order referenced by the returned index
     * @return the index of the first inactive status in use, or {@code -1} when none are in use
     */
    public int checkStatusUsuage(List<AppointmentStatus> allStatus);
}
