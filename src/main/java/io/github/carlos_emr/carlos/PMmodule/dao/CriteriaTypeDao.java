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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.CriteriaType;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing {@link CriteriaType} entities that define
 * the types of criteria used in vacancy matching.
 *
 * @since 2001-09-17
 * @see CriteriaType
 * @see CriteriaTypeDaoImpl
 */
public interface CriteriaTypeDao extends AbstractDao<CriteriaType> {

    /**
     * Retrieves all criteria types.
     *
     * @return List&lt;CriteriaType&gt; all criteria type records
     */
    public List<CriteriaType> findAll();

    /**
     * Finds a criteria type by its field name.
     *
     * @param fieldName String the field name to search for
     * @return CriteriaType the matching criteria type, or {@code null} if not found
     */
    public CriteriaType findByName(String fieldName);

    /**
     * Retrieves all criteria types for the default waitlist program (ID=1),
     * ordered by field type descending.
     *
     * @return List&lt;CriteriaType&gt; criteria types for program 1
     */
    public List<CriteriaType> getAllCriteriaTypes();

    /**
     * Retrieves all criteria types for a specific waitlist program,
     * ordered by field type descending.
     *
     * @param wlProgramId Integer the waitlist program ID
     * @return List&lt;CriteriaType&gt; criteria types for the specified program
     */
    public List<CriteriaType> getAllCriteriaTypesByWlProgramId(Integer wlProgramId);
}
 