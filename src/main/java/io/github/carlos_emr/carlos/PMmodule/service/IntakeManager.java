/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.service;

/**
 * Service interface for managing intake form configuration within the CARLOS EMR
 * Program Management module.
 *
 * <p>Provides configuration checks for the intake form subsystem, including whether
 * new client forms are in use and whether the intake module is enabled.</p>
 *
 * @since 2005
 */
public interface IntakeManager {

    /**
     * Checks whether the intake form is configured as a new client form.
     *
     * @return boolean {@code true} if the intake form is a new client form
     */
    public boolean isNewClientForm();

    /**
     * Checks whether the intake management module is enabled.
     *
     * @return boolean {@code true} if intake management is enabled
     */
    public boolean getEnabled();
}
