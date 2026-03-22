/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
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

import io.github.carlos_emr.carlos.commn.model.CdsClientForm;

/**
 * DAO interface for Clinical Decision Support (CDS) operations.
 *
 * @since 2001
 */

public interface CdsClientFormDao extends AbstractDao<CdsClientForm> {
    /**
     * Find Latest By Facility Client.
     *
     * @param facilityId Integer the facilityId
     * @param clientId Integer the clientId
     * @return CdsClientForm
     */
    CdsClientForm findLatestByFacilityClient(Integer facilityId, Integer clientId);

    /**
     * Find Latest By Facility Admission Id.
     *
     * @param facilityId Integer the facilityId
     * @param admissionId Integer the admissionId
     * @param signed Boolean the signed
     * @return CdsClientForm
     */
    CdsClientForm findLatestByFacilityAdmissionId(Integer facilityId, Integer admissionId, Boolean signed);

    /**
     * Find By Facility Client.
     *
     * @param facilityId Integer the facilityId
     * @param clientId Integer the clientId
     * @return List<CdsClientForm>
     */
    List<CdsClientForm> findByFacilityClient(Integer facilityId, Integer clientId);

    /**
     * Find Signed Cds Forms.
     *
     * @param facilityId Integer the facilityId
     * @param formVersion String the formVersion
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<CdsClientForm>
     */
    List<CdsClientForm> findSignedCdsForms(Integer facilityId, String formVersion, Date startDate, Date endDate);
}
