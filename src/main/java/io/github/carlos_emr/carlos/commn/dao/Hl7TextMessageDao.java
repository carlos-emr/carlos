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

import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;

/**
 * DAO interface for HL7 message operations.
 *
 * @since 2001
 */

public interface Hl7TextMessageDao extends AbstractDao<Hl7TextMessage> {

    /**
     * Find By File Upload Check Id.
     *
     * @param id int the id
     * @return List<Hl7TextMessage>
     */
    public List<Hl7TextMessage> findByFileUploadCheckId(int id);

    /**
     * Find By Ids.
     *
     * @param ids List<Integer> the ids
     * @return List<Hl7TextMessage>
     */
	public List<Hl7TextMessage> findByIds(List<Integer> ids);

    /**
     * Get Lab Results Since.
     *
     * @param demographicNo Integer the demographicNo
     * @param updateDate Date the updateDate
     * @return List<Integer>
     */
    public List<Integer> getLabResultsSince(Integer demographicNo, Date updateDate);

    /**
     * Find By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @param offset int the offset
     * @param limit int the limit
     * @return List<Hl7TextMessage>
     */
    public List<Hl7TextMessage> findByDemographicNo(Integer demographicNo, int offset, int limit);
}
