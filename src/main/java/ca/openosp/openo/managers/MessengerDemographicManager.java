//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.managers;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.MsgDemoMap;
import ca.openosp.openo.commn.model.MsgIntegratorDemoMap;
import ca.openosp.openo.utility.LoggedInInfo;

public interface MessengerDemographicManager {

    /**
     * Get all the demographic details that are attached to this message.
     * In most cases there is only 1 demographic, but, it is possible for 0 to many to be attached.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<Demographic> getAttachedDemographics(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Use this method if full demographic details are not required.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<MsgDemoMap> getAttachedDemographicList(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Retreive demographics from a remote Integrated facility that have not been linked with a local demographic.
     * The demographic number exists only in the remote facility until the user chooses to import it.
     * Once imported, the demographic number from the local AND remote facility will be attached.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<MsgIntegratorDemoMap> getUnlinkedIntegratedDemographicList(LoggedInInfo loggedInInfo, int messageId);

    public List<DemographicTransfer> getUnlinkedIntegratedDemographics(LoggedInInfo loggedInInfo, int messageId);

    public DemographicTransfer getIntegratedDemographic(LoggedInInfo loggedInInfo, int demographicNo, int facilityId);

    /**
     * This will extract a string of names and ages for each demographic attached to the given message id.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public String getAttachedDemographicNamesAndAges(LoggedInInfo loggedInInfo, int messageId);

    /**
     * Returns a Map of a Key: demographic number and Value: demographic name
     * Can be used to display a list of attached demographics.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public Map<Integer, String> getAttachedDemographicNameMap(LoggedInInfo loggedInInfo, int messageId);

    /**
     * ONLY FOR USE WITH DEMOGRAPHICS THAT ARE REMOTELY ATTACHED TO A MESSAGE - INTEGRATOR ONLY.
     *
     * @param loggedInInfo
     * @param messageId
     * @param demographicNoArray
     * @return
     */
    public Integer[] attachIntegratedDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, Integer[] demographicNoArray, int sourceFacilityId);

    /**
     * ONLY FOR USE WITH DEMOGRAPHICS THAT ARE REMOTELY ATTACHED TO A MESSAGE - INTEGRATOR ONLY.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param demographicNo the demographic number
     * @param sourceFacilityId the source facility ID
     * @return the attached demographic mapping ID
     */
    public Integer attachIntegratedDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, int demographicNo, int sourceFacilityId);


    /**
     * Get all the demographic ids from the given remote facility that are linked
     * to the given local demographic number.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @param sourceFacilityId
     * @return List<Integer>
     */
    public List<Integer> getLinkedDemographicIdsFromSourceFacility(LoggedInInfo loggedInInfo, final int demographicNo, int sourceFacilityId);

    /**
     * Search for the Integrated demographic entry and then update the associated msgDemoMapId. This helps indicate that the demographic
     * has been imported and attached.
     *
     * @param loggedInInfo
     * @param messageId
     * @param demographicNo
     * @param facilityId
     * @return long
     */
    public long updateAttachedIntegratedDemographic(LoggedInInfo loggedInInfo, int messageId, int demographicNo, int facilityId);

    /**
     * Attach an array of local Demographic numbers to the given message id
     * DO NOT USE TO ATTACH DEMOGRAPHICS FROM OUTSIDE FACILITIES.
     *
     * @param loggedInInfo
     * @param messageId
     * @param demographicNoArray
     * @return
     */
    public Long[] attachDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, Integer[] demographicNoArray);

    /**
     * Attach a demographic number to the given message id.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param demographicNo the demographic number
     * @return the message demographic mapping ID
     */
    public Long attachDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, int demographicNo);

    /**
     * This method is hard-coded to the most commons Integrator patient consent types.
     * UserProperty.INTEGRATOR_PATIENT_CONSENT
     * UserProperty.INTEGRATOR_DEMOGRAPHIC_CONSENT
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public boolean isPatientConsentedForIntegrator(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Gets a list of messages attached to the given demographic number
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public List<MsgDemoMap> getMessageMapByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo);

    /**
     * Import a demographic file and/or linking it to another file on the Integrator.
     * Returns null after a successful import or returns a list of Demographic objects if a user selection is required.
     *
     * @return
     */
    public List<Demographic> importDemographic(LoggedInInfo loggedInInfo, int remoteFacilityId, int remoteDemographicNo, int messageId);

    public boolean linkDemographicWithRemote(LoggedInInfo loggedInInfo, int demographicNo, int remoteFacilityId, int remoteDemographicNo, int messageId);

}
