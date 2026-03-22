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

/**
 * Service interface for managing clients (patients) within the CARLOS EMR Program Management module.
 *
 * <p>Provides operations for client demographics, referral management, joint admissions
 * (family head/dependent relationships), demographic extensions, and health card validation.
 * Supports the program queue workflow by processing referrals and creating queue entries.</p>
 *
 * @see ClientManagerImpl
 * @see Demographic
 * @see ClientReferral
 * @see JointAdmission
 * @since 2005
 */
public interface ClientManager {

    /**
     * Checks whether searching for clients outside of the provider's program domain is enabled.
     *
     * @return boolean {@code true} if outside-of-domain search is enabled
     */
    boolean isOutsideOfDomainEnabled();

    /**
     * Retrieves a client by their demographic number.
     *
     * @param demographicNo String the client demographic number
     * @return Demographic the client record, or {@code null} if not found or input is empty
     */
    Demographic getClientByDemographicNo(String demographicNo);

    /**
     * Retrieves all client records.
     *
     * @return List&lt;Demographic&gt; list of all client demographics
     */
    List<Demographic> getClients();

    /**
     * Searches for clients matching the specified criteria with filtering options.
     *
     * @param criteria ClientSearchFormBean the search criteria
     * @param returnOptinsOnly boolean whether to return only opted-in clients
     * @param excludeMerged boolean whether to exclude merged demographic records
     * @return List&lt;Demographic&gt; list of matching client records
     */
    List<Demographic> search(ClientSearchFormBean criteria, boolean returnOptinsOnly, boolean excludeMerged);

    /**
     * Searches for clients matching the specified criteria with default filtering.
     *
     * @param criteria ClientSearchFormBean the search criteria
     * @return List&lt;Demographic&gt; list of matching client records
     */
    List<Demographic> search(ClientSearchFormBean criteria);

    /**
     * Retrieves all client referrals.
     *
     * @return List&lt;ClientReferral&gt; list of all referrals
     */
    List<ClientReferral> getReferrals();

    /**
     * Retrieves all referrals for a specific client.
     *
     * @param clientId String the client identifier
     * @return List&lt;ClientReferral&gt; list of referrals for the client
     */
    List<ClientReferral> getReferrals(String clientId);

    /**
     * Retrieves referrals for a client within a specific facility.
     *
     * @param clientId Integer the client identifier
     * @param facilityId Integer the facility identifier
     * @return List&lt;ClientReferral&gt; list of referrals at the specified facility
     */
    List<ClientReferral> getReferralsByFacility(Integer clientId, Integer facilityId);

    /**
     * Retrieves active referrals for a client from a specific source facility.
     *
     * @param clientId String the client identifier
     * @param sourceFacilityId String the source facility identifier
     * @return List&lt;ClientReferral&gt; list of active referrals
     */
    List<ClientReferral> getActiveReferrals(String clientId, String sourceFacilityId);

    /**
     * Retrieves a specific client referral by its identifier.
     *
     * @param id String the referral identifier
     * @return ClientReferral the referral record
     */
    ClientReferral getClientReferral(String id);

    /**
     * Saves a client referral and adds it to the program queue if active.
     *
     * @param referral ClientReferral the referral to save
     */
    void saveClientReferral(ClientReferral referral);

    /**
     * Adds an active client referral to the corresponding program queue.
     *
     * @param referral ClientReferral the referral to queue
     */
    void addClientReferralToProgramQueue(ClientReferral referral);

    /**
     * Searches for referrals matching the specified criteria.
     *
     * @param referral ClientReferral the search criteria
     * @return List&lt;ClientReferral&gt; list of matching referrals
     */
    List<ClientReferral> searchReferrals(ClientReferral referral);

    /**
     * Saves a joint admission record linking a dependent to a family head.
     *
     * @param admission JointAdmission the joint admission to persist
     * @throws IllegalArgumentException if the admission is {@code null}
     */
    void saveJointAdmission(JointAdmission admission);

    /**
     * Retrieves all dependents (spouse and children) for a family head client.
     *
     * @param clientId Integer the family head client identifier
     * @return List&lt;JointAdmission&gt; list of joint admission records for dependents
     */
    List<JointAdmission> getDependents(Integer clientId);

    /**
     * Retrieves the demographic numbers of all dependents for a family head.
     *
     * @param clientId Integer the family head client identifier
     * @return List&lt;Integer&gt; list of dependent demographic numbers
     */
    List<Integer> getDependentsList(Integer clientId);

    /**
     * Retrieves the joint admission record for a client to determine their family head.
     *
     * @param clientId Integer the client identifier
     * @return JointAdmission the joint admission record, or {@code null} if not a dependent
     */
    JointAdmission getJointAdmission(Integer clientId);

    /**
     * Checks whether a client is a dependent member of a family.
     *
     * @param clientId Integer the client identifier
     * @return boolean {@code true} if the client has a family head assigned
     */
    boolean isClientDependentOfFamily(Integer clientId);

    /**
     * Checks whether a client is the head of a family with dependents.
     *
     * @param clientId Integer the client identifier
     * @return boolean {@code true} if the client has dependents
     */
    boolean isClientFamilyHead(Integer clientId);

    /**
     * Removes a joint admission record by client and provider.
     *
     * @param clientId Integer the client identifier
     * @param providerNo String the provider number
     */
    void removeJointAdmission(Integer clientId, String providerNo);

    /**
     * Removes a specific joint admission record.
     *
     * @param admission JointAdmission the joint admission to remove
     */
    void removeJointAdmission(JointAdmission admission);

    /**
     * Processes a client referral with default settings (no restriction override).
     *
     * @param referral ClientReferral the referral to process
     * @throws AlreadyAdmittedException if the client is already admitted to the target program
     * @throws AlreadyQueuedException if the client is already in the program queue
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processReferral(ClientReferral referral) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException;

    /**
     * Processes a client referral with an option to override service restrictions.
     * Saves the referral and queues the client and any dependents for admission.
     *
     * @param referral ClientReferral the referral to process
     * @param override boolean whether to bypass service restriction checks
     * @throws AlreadyAdmittedException if the client is already admitted to the target program
     * @throws AlreadyQueuedException if the client is already in the program queue
     * @throws ServiceRestrictionException if a service restriction is in place and not overridden
     */
    void processReferral(ClientReferral referral, boolean override) throws AlreadyAdmittedException, AlreadyQueuedException, ServiceRestrictionException;

    /**
     * Persists a client demographic record.
     *
     * @param client Demographic the client record to save
     */
    void saveClient(Demographic client);

    /**
     * Retrieves a demographic extension record by its identifier.
     *
     * @param id String the extension record identifier
     * @return DemographicExt the extension record
     */
    DemographicExt getDemographicExt(String id);

    /**
     * Retrieves all demographic extension records for a client.
     *
     * @param demographicNo int the client demographic number
     * @return List&lt;DemographicExt&gt; list of extension records
     */
    List<DemographicExt> getDemographicExtByDemographicNo(int demographicNo);

    /**
     * Retrieves a specific demographic extension by client and key.
     *
     * @param demographicNo int the client demographic number
     * @param key String the extension key name
     * @return DemographicExt the extension record, or {@code null} if not found
     */
    DemographicExt getDemographicExt(int demographicNo, String key);

    /**
     * Updates an existing demographic extension record.
     *
     * @param de DemographicExt the extension record to update
     */
    void updateDemographicExt(DemographicExt de);

    /**
     * Saves a demographic extension key-value pair for a client.
     *
     * @param demographicNo int the client demographic number
     * @param key String the extension key name
     * @param value String the extension value
     */
    void saveDemographicExt(int demographicNo, String key, String value);

    /**
     * Removes a demographic extension record by its identifier.
     *
     * @param id String the extension record identifier
     */
    void removeDemographicExt(String id);

    /**
     * Removes a demographic extension record by client and key.
     *
     * @param demographicNo int the client demographic number
     * @param key String the extension key name
     */
    void removeDemographicExt(int demographicNo, String key);

    /**
     * Checks whether a health card number already exists in the system.
     *
     * @param hin String the Health Insurance Number to check
     * @param hcType String the health card type code
     * @return boolean {@code true} if a matching health card exists
     */
    boolean checkHealthCardExists(String hin, String hcType);
}
