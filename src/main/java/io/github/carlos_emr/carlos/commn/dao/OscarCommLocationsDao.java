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

import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;

/**
 * DAO interface for core operations.
 *
 * @since 2001
 */

public interface OscarCommLocationsDao extends AbstractDao<OscarCommLocations> {

    /**
     * Find By Current1.
     *
     * @param current1 int the current1
     * @return List<OscarCommLocations>
     */
    public List<OscarCommLocations> findByCurrent1(int current1);

    /**
     * Find Form Location By Messsage Id.
     *
     * @param messId String the messId
     * @return List<Object[]>
     */
    public List<Object[]> findFormLocationByMesssageId(String messId);

    /**
     * Find Attachments By Message Id.
     *
     * @param messageId String the messageId
     * @return List<Object[]>
     */
    public List<Object[]> findAttachmentsByMessageId(String messageId);
}
