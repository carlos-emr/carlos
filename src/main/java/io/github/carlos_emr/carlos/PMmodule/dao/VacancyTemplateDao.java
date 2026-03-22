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

import io.github.carlos_emr.carlos.PMmodule.model.VacancyTemplate;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing {@link VacancyTemplate} entities that define
 * reusable templates for creating vacancies in the waitlist subsystem.
 *
 * @since 2001-09-17
 * @see VacancyTemplate
 * @see VacancyTemplateDaoImpl
 */
public interface VacancyTemplateDao extends AbstractDao<VacancyTemplate> {

    /**
     * Persists a new vacancy template.
     *
     * @param obj VacancyTemplate the template to save
     */
    public void saveVacancyTemplate(VacancyTemplate obj);

    /**
     * Merges (updates) an existing vacancy template.
     *
     * @param obj VacancyTemplate the template to merge
     */
    public void mergeVacancyTemplate(VacancyTemplate obj);

    /**
     * Retrieves a vacancy template by its ID.
     *
     * @param templateId Integer the template ID
     * @return VacancyTemplate the template, or {@code null} if not found
     */
    public VacancyTemplate getVacancyTemplate(Integer templateId);

    /**
     * Retrieves all vacancy templates for a specific waitlist program.
     *
     * @param wlProgramId Integer the waitlist program ID
     * @return List&lt;VacancyTemplate&gt; templates for the program
     */
    public List<VacancyTemplate> getVacancyTemplateByWlProgramId(Integer wlProgramId);

    /**
     * Retrieves only active vacancy templates for a specific waitlist program.
     *
     * @param wlProgramId Integer the waitlist program ID
     * @return List&lt;VacancyTemplate&gt; active templates for the program
     */
    public List<VacancyTemplate> getActiveVacancyTemplatesByWlProgramId(Integer wlProgramId);
}
