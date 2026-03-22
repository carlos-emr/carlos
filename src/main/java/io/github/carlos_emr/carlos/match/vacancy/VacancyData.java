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
package io.github.carlos_emr.carlos.match.vacancy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Data transfer object holding the matching criteria for a program vacancy.
 *
 * <p>Contains the vacancy and program identifiers along with a map of template data
 * entries keyed by parameter name. Each {@link VacancyTemplateData} entry defines
 * the matching criteria (values, ranges, weights) for a specific vacancy attribute.</p>
 *
 * @see VacancyTemplateData
 * @see Matcher
 * @since 2026-03-17
 */
public class VacancyData {
    private int vacancy_id;
    private int program_id;
    private Map<String, VacancyTemplateData> vacancyData = new HashMap<String, VacancyTemplateData>();

    /**
     * Returns the vacancy identifier.
     *
     * @return int the vacancy identifier
     */
    public int getVacancy_id() {
        return vacancy_id;
    }

    /**
     * Sets the vacancy identifier.
     *
     * @param vacancy_id int the vacancy identifier to set
     */
    public void setVacancy_id(int vacancy_id) {
        this.vacancy_id = vacancy_id;
    }

    /**
     * Returns the program identifier associated with this vacancy.
     *
     * @return int the program identifier
     */
    public int getProgram_id() {
        return program_id;
    }

    /**
     * Sets the program identifier associated with this vacancy.
     *
     * @param program_id int the program identifier to set
     */
    public void setProgram_id(int program_id) {
        this.program_id = program_id;
    }

    /**
     * Returns the map of vacancy matching criteria keyed by parameter name.
     *
     * @return Map of String parameter names to {@link VacancyTemplateData} criteria
     */
    public Map<String, VacancyTemplateData> getVacancyData() {
        return vacancyData;
    }

    /**
     * Sets the map of vacancy matching criteria.
     *
     * @param vacancyData Map of String parameter names to {@link VacancyTemplateData} criteria
     */
    public void setVacancyData(Map<String, VacancyTemplateData> vacancyData) {
        this.vacancyData = vacancyData;
    }

    @Override
    public String toString() {
        final int maxLen = 16;
        return "VacancyData [vacancy_id="
                + vacancy_id
                + ", program_id="
                + program_id
                + ", vacancyData="
                + (vacancyData != null ? toString(vacancyData.entrySet(),
                maxLen) : null) + "]";
    }

    private String toString(Collection<?> collection, int maxLen) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int i = 0;
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext()
                && i < maxLen; i++) {
            if (i > 0)
                builder.append(", ");
            builder.append(iterator.next());
        }
        builder.append("]");
        return builder.toString();
    }

}
