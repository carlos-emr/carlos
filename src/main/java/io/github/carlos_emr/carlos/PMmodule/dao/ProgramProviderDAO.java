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
 */

package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.model.Facility;

import java.util.List;

public interface ProgramProviderDAO {

    public List<ProgramProvider> getProgramProviderByProviderProgramId(String providerNo, Long programId);

    public List<ProgramProvider> getAllProgramProviders();

    public List<ProgramProvider> getProgramProviderByProviderNo(String providerNo);

    public List<ProgramProvider> getProgramProviders(Long programId);

    public List<ProgramProvider> getProgramProvidersByProvider(String providerNo);

    public List getProgramProvidersByProviderAndFacility(String providerNo, Integer facilityId);

    public ProgramProvider getProgramProvider(Long id);

    public ProgramProvider getProgramProvider(String providerNo, Long programId);

    public ProgramProvider getProgramProvider(String providerNo, Long programId, Long roleId);

    public void saveProgramProvider(ProgramProvider pp);

    public void deleteProgramProvider(Long id);

    public void deleteProgramProviderByProgramId(Long programId);

    public List<ProgramProvider> getProgramProvidersInTeam(Integer programId, Integer teamId);

    public List<ProgramProvider> getProgramDomain(String providerNo);

    public List<ProgramProvider> getActiveProgramDomain(String providerNo);

    public List<ProgramProvider> getProgramDomainByFacility(String providerNo, Integer facilityId);

    public boolean isThisProgramInProgramDomain(String providerNo, Integer programId);

    public List<Facility> getFacilitiesInProgramDomain(String providerNo);

    public void updateProviderRole(ProgramProvider pp, Long roleId);
}
