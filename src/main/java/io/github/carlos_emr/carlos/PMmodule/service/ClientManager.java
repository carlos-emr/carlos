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

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyAdmittedException;
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyQueuedException;
import io.github.carlos_emr.carlos.PMmodule.exception.ServiceRestrictionException;
import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;

public interface ClientManager {

    boolean isOutsideOfDomainEnabled();

    Demographic getClientByDemographicNo(String demographicNo);

    List<Demographic> getClients();

    List<Demographic> search(ClientSearchFormBean criteria, boolean returnOptinsOnly, boolean excludeMerged);

    List<Demographic> search(ClientSearchFormBean criteria);

    List<ClientReferral> getReferrals();

    List<ClientReferral> getReferrals(String clientId);

    List<ClientReferral> getReferralsByFacility(Integer clientId, Integer facilityId);

    List<ClientReferral> getActiveReferrals(String clientId, String sourceFacilityId);

    ClientReferral getClientReferral(String id);

    void saveClientReferral(ClientReferral referral);

    void addClientReferralToProgramQueue(ClientReferral referral);

    List<ClientReferral> searchReferrals(ClientReferral referral);

    void saveJointAdmission(JointAdmission admission);

    List<JointAdmission> getDependents(Integer clientId);

    List<Integer> getDependentsList(Integer clientId);

    JointAdmission getJointAdmission(Integer clientId);

    boolean isClientDependentOfFamily(Integer clientId);

    boolean isClientFamilyHead(Integer clientId);

    void removeJointAdmission(Integer clientId, String providerNo);

    void removeJointAdmission(JointAdmission admission);

    void processReferral(ClientReferral referral) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException;

    void processReferral(ClientReferral referral, boolean override) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException;

    void saveClient(Demographic client);

    DemographicExt getDemographicExt(String id);

    List<DemographicExt> getDemographicExtByDemographicNo(int demographicNo);

    DemographicExt getDemographicExt(int demographicNo, String key);

    void updateDemographicExt(DemographicExt de);

    void saveDemographicExt(int demographicNo, String key, String value);

    void removeDemographicExt(String id);

    void removeDemographicExt(int demographicNo, String key);

    boolean checkHealthCardExists(String hin, String hcType);
}
