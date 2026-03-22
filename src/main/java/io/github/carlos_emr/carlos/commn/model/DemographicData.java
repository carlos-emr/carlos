package io.github.carlos_emr.carlos.commn.model;

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
/**
 * Interface marking entities that are associated with a specific patient (demographic).
 *
 * <p>Implemented by model classes such as {@link Allergy}, {@link Appointment},
 * {@link Prevention}, and others that contain patient-specific data and need
 * to expose the demographic number for cross-entity queries and filtering.</p>
 *
 * @see Demographic
 * @since 2001-01-01
 */
public interface DemographicData {

    /**
     * Returns the unique identifier for this entity.
     *
     * @return Integer the entity ID
     */
    Integer getId();

    /**
     * Returns the demographic (patient) number this entity is associated with.
     *
     * @return int the demographic number
     */
    int getDemographicNo();
}
