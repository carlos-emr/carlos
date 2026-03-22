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

import io.github.carlos_emr.carlos.PMmodule.model.Criteria;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing {@link Criteria} entities used in
 * vacancy matching within the waitlist subsystem.
 *
 * <p>Provides methods to query criteria by template, vacancy, and type,
 * as well as refined criteria that exclude ad-hoc entries.</p>
 *
 * @since 2001-09-17
 * @see Criteria
 * @see CriteriaDaoImpl
 */
public interface CriteriaDao extends AbstractDao<Criteria> {

    /**
     * Retrieves all criteria for a specific vacancy template.
     *
     * @param templateId Integer the vacancy template ID
     * @return List&lt;Criteria&gt; criteria for the specified template
     */
    public List<Criteria> getCriteriaByTemplateId(Integer templateId);

    /**
     * Retrieves a single criteria record by template ID, vacancy ID, and criteria type ID.
     *
     * <p>Handles cases where template ID or vacancy ID may be {@code null}.</p>
     *
     * @param templateId Integer the vacancy template ID, or {@code null}
     * @param vacancyId Integer the vacancy ID, or {@code null}
     * @param typeId Integer the criteria type ID
     * @return Criteria the matching criteria, or {@code null} if not found
     */
    public Criteria getCriteriaByTemplateIdVacancyIdTypeId(Integer templateId, Integer vacancyId, Integer typeId);

    /**
     * Retrieves all criteria associated with a specific vacancy.
     *
     * @param vacancyId Integer the vacancy ID
     * @return List&lt;Criteria&gt; criteria for the specified vacancy
     */
    public List<Criteria> getCriteriasByVacancyId(Integer vacancyId);

    /**
     * Retrieves non-ad-hoc criteria for a specific vacancy.
     *
     * <p>Excludes criteria where {@code canBeAdhoc} is 0 (meaning they should not
     * appear in vacancy forms).</p>
     *
     * @param vacancyId Integer the vacancy ID
     * @return List&lt;Criteria&gt; refined criteria for the vacancy
     */
    public List<Criteria> getRefinedCriteriasByVacancyId(Integer vacancyId);

    /**
     * Retrieves non-ad-hoc criteria for a specific template.
     *
     * @param templateId Integer the vacancy template ID
     * @return List&lt;Criteria&gt; refined criteria for the template
     */
    public List<Criteria> getRefinedCriteriasByTemplateId(Integer templateId);


}
 