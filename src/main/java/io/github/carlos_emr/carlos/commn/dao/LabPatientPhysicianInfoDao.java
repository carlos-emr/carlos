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

import io.github.carlos_emr.carlos.commn.model.LabPatientPhysicianInfo;

public interface LabPatientPhysicianInfoDao extends AbstractDao<LabPatientPhysicianInfo> {
    List<Object[]> findRoutings(Integer demographicNo, String labType);

    List<Object[]> findByPatientName(String status, String labType, String providerNo, String patientLastName, String patientFirstName, String patientHealthNumber);

    List<Object[]> findByDemographic(Integer demographicNo, String labType);

    /**
     * Finds the reports that share a CML report's accession number, in print order.
     *
     * <p>Each row starts with {@code {Integer id, String serviceDate, String selectedServiceDate}}: the sibling's id,
     * its {@code service_date}, and the selected report's {@code service_date}. Both dates are the
     * {@code varchar(10)} column the vendor fills with {@code YYYYMMDD}, so they are {@code String}s, never
     * {@code Date}s, and may be null or damaged. The selected report is one of its own rows unless it has no
     * accession number. Missing print headers remain visible to validation. A report with a blank
     * accession number yields no rows. Columns 3 and 4 are the candidate and selected
     * {@link LabPatientPhysicianInfo} entities; columns 5 and 6 are the nullable print date and time.
     *
     * @param labId the selected report's identifier
     * @return the rows, oldest print first and, for reports of one print run, in import order; empty when the
     *         report has no accession number to match on
     */
    List<Object[]> findLabServiceDatesByLabId(Integer labId);

    List<Integer> getLabResultsSince(Integer demographicNo, Date updateDate);
}
