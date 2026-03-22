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

import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.commn.dao.AbstractDao;

/**
 * Data access interface for managing {@link Vacancy} entities in the
 * waitlist matching subsystem.
 *
 * @since 2001-09-17
 * @see Vacancy
 * @see VacancyDaoImpl
 */
public interface VacancyDao extends AbstractDao<Vacancy> {

    /**
     * Retrieves vacancies for a specific waitlist program, ordered by name.
     *
     * @param wlProgramId Integer the waitlist program ID
     * @return List&lt;Vacancy&gt; vacancies for the program
     */
    public List<Vacancy> getVacanciesByWlProgramId(Integer wlProgramId);

    /**
     * Retrieves vacancies for a waitlist program filtered by status.
     *
     * @param wlProgramId Integer the waitlist program ID
     * @param status String the vacancy status filter
     * @return List&lt;Vacancy&gt; matching vacancies ordered by name
     */
    public List<Vacancy> getVacanciesByWlProgramIdAndStatus(Integer wlProgramId, String status);

    /**
     * Retrieves vacancies by exact name.
     *
     * @param vacancyName String the vacancy name
     * @return List&lt;Vacancy&gt; matching vacancies ordered by name
     */
    public List<Vacancy> getVacanciesByName(String vacancyName);

    /**
     * Finds vacancies by status and vacancy ID.
     *
     * @param status String the vacancy status
     * @param vacancyId int the vacancy ID
     * @return List&lt;Vacancy&gt; matching vacancies
     */
    public List<Vacancy> findByStatusAndVacancyId(String status, int vacancyId);

    /**
     * Retrieves a single vacancy by its ID.
     *
     * @param vacancyId int the vacancy ID
     * @return Vacancy the vacancy, or {@code null} if not found
     */
    public Vacancy getVacancyById(int vacancyId);

    /**
     * Retrieves all currently active vacancies.
     *
     * @return List&lt;Vacancy&gt; active vacancies
     */
    public List<Vacancy> findCurrent();
}
