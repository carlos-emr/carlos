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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;

/**
 * DAO interface for managing archived appointment records.
 * <p>
 * Supports the appointment history tracking system by providing operations to
 * archive current appointments and query archived records by update date.
 *
 * @since 2001
 */
public interface AppointmentArchiveDao extends AbstractDao<AppointmentArchive> {

    /**
     * Creates an archive copy of the given appointment.
     * Copies all properties from the appointment (except ID) into a new archive record.
     *
     * @param appointment the {@link Appointment} to archive
     * @return the newly created {@link AppointmentArchive} record
     */
    public AppointmentArchive archiveAppointment(Appointment appointment);

    /**
     * Finds archived appointments updated after the specified date, ordered by update date.
     *
     * @param updatedAfterThisDateExclusive Date the cutoff date (exclusive)
     * @param itemsToReturn                 int the maximum number of results to return
     * @return List of {@link AppointmentArchive} records ordered by update date ascending
     */
    public List<AppointmentArchive> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);
}
