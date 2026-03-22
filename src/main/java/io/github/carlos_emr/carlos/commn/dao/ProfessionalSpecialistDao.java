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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;

/**
 * DAO interface for professional contact operations.
 *
 * @since 2001
 */

public interface ProfessionalSpecialistDao extends AbstractDao<ProfessionalSpecialist> {

    /**
     * Find All.
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findAll();

    /**
     * Find By E Data Url Not Null.
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByEDataUrlNotNull();

    /**
     * Find By Full Name.
     *
     * @param lastName String the lastName
     * @param firstName String the firstName
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByFullName(String lastName, String firstName);

    /**
     * Find By Last Name.
     *
     * @param lastName String the lastName
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByLastName(String lastName);

    /**
     * Find By Specialty.
     *
     * @param specialty String the specialty
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findBySpecialty(String specialty);

    /**
     * Find By Referral No.
     *
     * @param referralNo String the referralNo
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByReferralNo(String referralNo);

    /**
     * Get By Referral No.
     *
     * @param referralNo String the referralNo
     * @return ProfessionalSpecialist
     */
    ProfessionalSpecialist getByReferralNo(String referralNo);

    /**
     * Has Remote Capable Professional Specialists.
     * @return boolean
     */
    boolean hasRemoteCapableProfessionalSpecialists();

    /**
     * Search.
     *
     * @param keyword String the keyword
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> search(String keyword);

    /**
     * Find By Full Name And Specialty And Address.
     *
     * @param lastName String the lastName
     * @param firstName String the firstName
     * @param specialty String the specialty
     * @param address String the address
     * @param showHidden Boolean the showHidden
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByFullNameAndSpecialtyAndAddress(String lastName, String firstName, String specialty, String address, Boolean showHidden);

    /**
     * Find By Service.
     *
     * @param serviceName String the serviceName
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByService(String serviceName);

    /**
     * Find By Service Id.
     *
     * @param serviceId Integer the serviceId
     * @return List<ProfessionalSpecialist>
     */
    List<ProfessionalSpecialist> findByServiceId(Integer serviceId);
}
