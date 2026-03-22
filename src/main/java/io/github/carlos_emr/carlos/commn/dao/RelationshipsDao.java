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

import io.github.carlos_emr.carlos.commn.model.Relationships;

/**
 * DAO interface for relationship operations.
 *
 * @since 2001
 */

public interface RelationshipsDao extends AbstractDao<Relationships> {
    /**
     * Find Active.
     *
     * @param id Integer the id
     * @return Relationships
     */
    Relationships findActive(Integer id);

    /**
     * Find By Demographic Number.
     *
     * @param demographicNumber Integer the demographicNumber
     * @return List<Relationships>
     */
    List<Relationships> findByDemographicNumber(Integer demographicNumber);

    /**
     * Find Active Sub Decision Maker.
     *
     * @param demographicNumber Integer the demographicNumber
     * @return List<Relationships>
     */
    List<Relationships> findActiveSubDecisionMaker(Integer demographicNumber);

    /**
     * Find Active By Demographic Number And Facility.
     *
     * @param demographicNumber Integer the demographicNumber
     * @param facilityId Integer the facilityId
     * @return List<Relationships>
     */
    List<Relationships> findActiveByDemographicNumberAndFacility(Integer demographicNumber, Integer facilityId);
}
