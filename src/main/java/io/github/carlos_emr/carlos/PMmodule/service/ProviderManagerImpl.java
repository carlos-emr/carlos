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
package io.github.carlos_emr.carlos.PMmodule.service;

import io.github.carlos_emr.carlos.PMmodule.dao.AgencyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.Agency;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Transactional implementation of {@link ProviderManager} for managing healthcare providers
 * within the CARLOS EMR Program Management module.
 *
 * <p>Delegates to provider, agency, program provider, and security user role DAOs for
 * data access operations.</p>
 *
 * @see ProviderManager
 * @see Provider
 * @since 2005
 */
@Transactional
public class ProviderManagerImpl implements ProviderManager {
    private ProviderDao providerDao;
    private AgencyDao agencyDao;
    private ProgramProviderDAO programProviderDAO;
    private SecUserRoleDao secUserRoleDao;

    /** {@inheritDoc} */
    public void setProviderDao(ProviderDao providerDao) {
        this.providerDao = providerDao;
    }

    /** {@inheritDoc} */
    public void setAgencyDao(AgencyDao agencyDao) {
        this.agencyDao = agencyDao;
    }

    /** {@inheritDoc} */
    public void setProgramProviderDAO(ProgramProviderDAO dao) {
        this.programProviderDAO = dao;
    }

    /** {@inheritDoc} */
    public void setSecUserRoleDao(SecUserRoleDao secUserRoleDao) {
        this.secUserRoleDao = secUserRoleDao;
    }

    public Provider getProvider(String providerNo) {
        return providerDao.getProvider(providerNo);
    }

    public String getProviderName(String providerNo) {
        return providerDao.getProviderName(providerNo);
    }

    public List<Provider> getProviders() {
        return providerDao.getProviders();
    }

    public List<Provider> getActiveProviders() {
        return providerDao.getActiveProviders();
    }

    public List<Provider> getActiveProviders(String facilityId, String programId) {
        return providerDao.getActiveProviders(facilityId, programId);
    }

    public List<Provider> getActiveProviders(String providerNo, Integer shelterId) {
        return providerDao.getActiveProviders(providerNo, shelterId);
    }

    public List<Provider> search(String name) {
        return providerDao.search(name);
    }

    public List<ProgramProvider> getProgramDomain(String providerNo) {
        return programProviderDAO.getProgramDomain(providerNo);
    }

    public List<ProgramProvider> getProgramDomainByFacility(String providerNo, Integer facilityId) {
        return programProviderDAO.getProgramDomainByFacility(providerNo, facilityId);
    }

    public List<Facility> getFacilitiesInProgramDomain(String providerNo) {
        return programProviderDAO.getFacilitiesInProgramDomain(providerNo);
    }


    public List<Agency> getAgencyDomain(String providerNo) {
        Agency localAgency = agencyDao.getLocalAgency();
        List<Agency> agencies = new ArrayList<Agency>();
        agencies.add(localAgency);
        return agencies;
    }

    public List<Provider> getProvidersByType(String type) {
        return providerDao.getProvidersByType(type);
    }

    public List<SecUserRole> getSecUserRoles(String providerNo) {
        return secUserRoleDao.getUserRoles(providerNo);
    }

    public void saveUserRole(SecUserRole sur) {
        secUserRoleDao.save(sur);
    }
}
