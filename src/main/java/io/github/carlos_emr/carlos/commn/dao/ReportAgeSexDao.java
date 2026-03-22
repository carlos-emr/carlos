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

import io.github.carlos_emr.carlos.commn.model.ReportAgeSex;

/**
 * DAO interface for reporting operations.
 *
 * @since 2001
 */

public interface ReportAgeSexDao extends AbstractDao<ReportAgeSex> {
    /**
     * Find Before Report Date.
     *
     * @param reportDate Date the reportDate
     * @return List<ReportAgeSex>
     */
    List<ReportAgeSex> findBeforeReportDate(Date reportDate);

    /**
     * Delete All By Date.
     *
     * @param reportDate Date the reportDate
     */
    void deleteAllByDate(Date reportDate);

    /**
     * Populate All.
     *
     * @param yearOfBirth String the yearOfBirth
     */
    void populateAll(String yearOfBirth);

    /**
     * Count_reportagesex_roster.
     *
     * @param roster String the roster
     * @param sex String the sex
     * @param providerNo String the providerNo
     * @param age int the age
     * @param dateStarted Date the dateStarted
     * @param dateEnded Date the dateEnded
     * @return Long
     */
    Long count_reportagesex_roster(String roster, String sex, String providerNo, int age, Date dateStarted, Date dateEnded);

    /**
     * Count_reportagesex_noroster.
     *
     * @param roster String the roster
     * @param sex String the sex
     * @param providerNo String the providerNo
     * @param minAge int the minAge
     * @param maxAge int the maxAge
     * @param dateStarted Date the dateStarted
     * @param dateEnded Date the dateEnded
     * @return Long
     */
    Long count_reportagesex_noroster(String roster, String sex, String providerNo, int minAge, int maxAge, Date dateStarted, Date dateEnded);

    /**
     * Count_reportagesex.
     *
     * @param roster String the roster
     * @param sex String the sex
     * @param providerNo String the providerNo
     * @param minAge int the minAge
     * @param maxAge int the maxAge
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return Long
     */
    Long count_reportagesex(String roster, String sex, String providerNo, int minAge, int maxAge, Date startDate, Date endDate);
}
