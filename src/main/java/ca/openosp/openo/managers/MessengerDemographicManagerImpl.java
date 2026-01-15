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

import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.caisi_integrator.ws.DemographicTransfer;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.commn.dao.MsgDemoMapDao;
import ca.openosp.openo.commn.dao.MsgIntegratorDemoMapDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.MsgDemoMap;
import ca.openosp.openo.commn.model.MsgIntegratorDemoMap;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessengerDemographicManagerImpl implements MessengerDemographicManager {

    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private DemographicManager demographicManager;
    @Autowired
    private MsgDemoMapDao msgDemoMapDao;
    @Autowired
    private MsgIntegratorDemoMapDao msgIntegratorDemoMapDao;

    /**
     * Get all the demographic details that are attached to this message.
     * In most cases there is only 1 demographic, but, it is possible for 0 to many to be attached.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<Demographic> getAttachedDemographics(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MsgDemoMap> msgDemoMap = getAttachedDemographicList(loggedInInfo, messageId);
        Set<Demographic> demographicList = new HashSet<Demographic>();
        if (msgDemoMap != null) {
            for (MsgDemoMap msgDemo : msgDemoMap) {
                Demographic demographic = getAttachedDemographic(loggedInInfo, msgDemo.getDemographic_no());
                demographicList.add(demographic);
            }
        }
        return new ArrayList<Demographic>(demographicList);
    }

    private Demographic getAttachedDemographic(LoggedInInfo loggedInInfo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        return demographicManager.getDemographic(loggedInInfo, demographicNo);
    }

    /**
     * Use this method if full demographic details are not required.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<MsgDemoMap> getAttachedDemographicList(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        return msgDemoMapDao.findByMessageId(messageId);
    }

    /**
     * Retreive demographics from a remote Integrated facility that have not been linked with a local demographic.
     * The demographic number exists only in the remote facility until the user chooses to import it.
     * Once imported, the demographic number from the local AND remote facility will be attached.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public List<MsgIntegratorDemoMap> getUnlinkedIntegratedDemographicList(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        return msgIntegratorDemoMapDao.findByMessageIdandMsgDemoMapId(messageId, 0L);
    }

    public List<DemographicTransfer> getUnlinkedIntegratedDemographics(LoggedInInfo loggedInInfo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        List<MsgIntegratorDemoMap> unlinkedList = getUnlinkedIntegratedDemographicList(loggedInInfo, messageId);
        List<DemographicTransfer> demographicTransferList = new ArrayList<DemographicTransfer>();
        for (MsgIntegratorDemoMap msgIntegratorDemoMap : unlinkedList) {
            DemographicTransfer demographicTransfer = getIntegratedDemographic(loggedInInfo, msgIntegratorDemoMap.getSourceDemographicNo(), msgIntegratorDemoMap.getSourceFacilityId());
            if (demographicTransfer != null) {
                demographicTransferList.add(demographicTransfer);
            }
        }
        return demographicTransferList;
    }

    public DemographicTransfer getIntegratedDemographic(LoggedInInfo loggedInInfo, int demographicNo, int facilityId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        DemographicTransfer demographicTransfer = null;
        try {
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            demographicTransfer = demographicWs.getDemographicByFacilityIdAndDemographicId(facilityId, demographicNo);
        } catch (MalformedURLException e) {
            MiscUtils.getLogger().error("error", e);
        }
        return demographicTransfer;
    }

    /**
     * This will extract a string of names and ages for each demographic attached to the given message id.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public String getAttachedDemographicNamesAndAges(LoggedInInfo loggedInInfo, int messageId) {
        List<Demographic> demographicList = getAttachedDemographics(loggedInInfo, messageId);
        StringBuilder stringBuilder = new StringBuilder("");
        String separator = "";
        for (Demographic demographic : demographicList) {
            stringBuilder.append(demographic.getLastName() + ", ");
            stringBuilder.append(demographic.getFirstName() + " ");
            stringBuilder.append(demographic.getAge() + " years");
            stringBuilder.append(separator);
            separator = "; ";
        }
        return stringBuilder.toString();
    }

    /**
     * Returns a Map of a Key: demographic number and Value: demographic name
     * Can be used to display a list of attached demographics.
     *
     * @param loggedInInfo
     * @param messageId
     * @return
     */
    public Map<Integer, String> getAttachedDemographicNameMap(LoggedInInfo loggedInInfo, int messageId) {
        List<Demographic> demographicList = getAttachedDemographics(loggedInInfo, messageId);
        Map<Integer, String> demographicMap = new HashMap<Integer, String>();

        for (Demographic demographic : demographicList) {
            demographicMap.put(demographic.getDemographicNo(), demographic.getFormattedName());
        }
        return demographicMap;
    }

    /**
     * ONLY FOR USE WITH DEMOGRAPHICS THAT ARE REMOTELY ATTACHED TO A MESSAGE - INTEGRATOR ONLY.
     *
     * @param loggedInInfo
     * @param messageId
     * @param demographicNoArray
     * @return
     */
    public Integer[] attachIntegratedDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, Integer[] demographicNoArray, int sourceFacilityId) {
        List<Integer> demoMapIdList = new ArrayList<Integer>();
        for (int demographicNo : demographicNoArray) {
            Integer id = attachIntegratedDemographicToMessage(loggedInInfo, messageId, demographicNo, sourceFacilityId);
            demoMapIdList.add(id);
        }
        return demoMapIdList.toArray(new Integer[demoMapIdList.size()]);
    }

    /**
     * ONLY FOR USE WITH DEMOGRAPHICS THAT ARE REMOTELY ATTACHED TO A MESSAGE - INTEGRATOR ONLY.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param demographicNo the demographic number
     * @param sourceFacilityId the source facility ID
     * @return the attached demographic mapping ID
     */
    public Integer attachIntegratedDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, int demographicNo, int sourceFacilityId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        int msgDemoMapId = 0;


        MsgIntegratorDemoMap msgIntegratorDemoMap = new MsgIntegratorDemoMap();
        msgIntegratorDemoMap.setMessageId(messageId);
        msgIntegratorDemoMap.setSourceDemographicNo(demographicNo);
        msgIntegratorDemoMap.setSourceFacilityId(sourceFacilityId);
        msgIntegratorDemoMap.setMsgDemoMapId(msgDemoMapId);

        msgIntegratorDemoMapDao.persist(msgIntegratorDemoMap);
        return msgIntegratorDemoMap.getId();
    }


    /**
     * Get all the demographic ids from the given remote facility that are linked
     * to the given local demographic number.
     *
     * @param loggedInInfo
     * @param demographicNo
     * @param sourceFacilityId
     * @return List<Integer>
     */
    public List<Integer> getLinkedDemographicIdsFromSourceFacility(LoggedInInfo loggedInInfo, final int demographicNo, int sourceFacilityId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        return demographicManager.getLinkedDemographicIds(loggedInInfo, demographicNo, sourceFacilityId);
    }

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
    public long updateAttachedIntegratedDemographic(LoggedInInfo loggedInInfo, int messageId, int demographicNo, int facilityId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        // first check the list to avoid duplicates.
        List<MsgDemoMap> attachedDemographics = getAttachedDemographicList(loggedInInfo, messageId);
        if (attachedDemographics != null) {
            for (MsgDemoMap msgDemoMap : attachedDemographics) {
                if (msgDemoMap.getDemographic_no() == demographicNo) {
                    return msgDemoMap.getId();
                }
            }
        }

        long msgDemoMapId = attachDemographicToMessage(loggedInInfo, messageId, demographicNo);

        // this is a one to one relationship. One message to One integrated demographic
        List<MsgIntegratorDemoMap> msgIntegratorDemoMapList = getUnlinkedIntegratedDemographicList(loggedInInfo, messageId);
        if (msgIntegratorDemoMapList != null) {
            for (MsgIntegratorDemoMap msgIntegratorDemoMap : msgIntegratorDemoMapList) {
                if (msgIntegratorDemoMap.getSourceFacilityId() == facilityId) {
                    msgIntegratorDemoMap.setMsgDemoMapId(msgDemoMapId);
                    msgIntegratorDemoMapDao.merge(msgIntegratorDemoMap);
                }
            }
        }

        return msgDemoMapId;
    }

    /**
     * Attach an array of local Demographic numbers to the given message id
     * DO NOT USE TO ATTACH DEMOGRAPHICS FROM OUTSIDE FACILITIES.
     *
     * @param loggedInInfo
     * @param messageId
     * @param demographicNoArray
     * @return
     */
    public Long[] attachDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, Integer[] demographicNoArray) {
        List<Long> demoMapIdList = new ArrayList<Long>();
        for (int demographicNo : demographicNoArray) {
            Long id = attachDemographicToMessage(loggedInInfo, messageId, demographicNo);
            demoMapIdList.add(id);
        }
        return demoMapIdList.toArray(new Long[demoMapIdList.size()]);
    }

    /**
     * Attach a demographic number to the given message id.
     *
     * @param loggedInInfo the logged in user information
     * @param messageId the message ID
     * @param demographicNo the demographic number
     * @return the message demographic mapping ID
     */
    public Long attachDemographicToMessage(LoggedInInfo loggedInInfo, int messageId, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, demographicNo)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        MsgDemoMap msgDemoMap = new MsgDemoMap();
        msgDemoMap.setDemographic_no(demographicNo);
        msgDemoMap.setMessageID(messageId);
        msgDemoMapDao.persist(msgDemoMap);
        return msgDemoMap.getId();
    }

    /**
     * This method is hard-coded to the most commons Integrator patient consent types.
     * UserProperty.INTEGRATOR_PATIENT_CONSENT
     * UserProperty.INTEGRATOR_DEMOGRAPHIC_CONSENT
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public boolean isPatientConsentedForIntegrator(LoggedInInfo loggedInInfo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        /*
         * it is acceptable to remove the hard-coded consent types in the futre
         * Or any additional Integrator consent types can be added.  Dealers choice.
         * For now, this intention for this method is to check the most commonly used Integrator consent types.
         */
        return demographicManager.isPatientConsented(loggedInInfo, demographicNo, UserProperty.INTEGRATOR_PATIENT_CONSENT)
                || demographicManager.isPatientConsented(loggedInInfo, demographicNo, UserProperty.INTEGRATOR_DEMOGRAPHIC_CONSENT);
    }

    /**
     * Gets a list of messages attached to the given demographic number
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public List<MsgDemoMap> getMessageMapByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        return msgDemoMapDao.findByDemographicNo(demographicNo);
    }

    /**
     * Import a demographic file and/or linking it to another file on the Integrator.
     * Returns null after a successful import or returns a list of Demographic objects if a user selection is required.
     *
     * @return
     */
    public List<Demographic> importDemographic(LoggedInInfo loggedInInfo, int remoteFacilityId, int remoteDemographicNo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }

        Demographic remoteDemographic = demographicManager.getRemoteDemographic(loggedInInfo, remoteFacilityId, remoteDemographicNo);
        Demographic exactMatch = demographicManager.findExactMatchToDemographic(loggedInInfo, remoteDemographic);

        // first try an exact match, and hopefully save the user some time.
        if (exactMatch != null) {
            // link the local demographic with the integrator and return null if an exact match is found.
            linkDemographicWithRemote(loggedInInfo, exactMatch.getDemographicNo(), remoteFacilityId, remoteDemographicNo, messageId);
            return null;
        }

        // try a fuzzy match next
        List<Demographic> fuzzyMatches = demographicManager.findFuzzyMatchToDemographic(loggedInInfo, remoteDemographic);
        if (fuzzyMatches != null && fuzzyMatches.size() > 0) {
            // return the fuzzy matches for the user to select from
            return fuzzyMatches;
        }

        // there are no matches at this point, so just import the remote demographic as new.
        else {
            Demographic newDemographic = demographicManager.copyRemoteDemographic(loggedInInfo, remoteDemographic, remoteFacilityId, remoteDemographicNo);
            updateAttachedIntegratedDemographic(loggedInInfo, messageId, newDemographic.getDemographicNo(), remoteFacilityId);
        }

        return null;
    }

    public boolean linkDemographicWithRemote(LoggedInInfo loggedInInfo, int demographicNo, int remoteFacilityId, int remoteDemographicNo, int messageId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_msg", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_msg)");
        }
        boolean success = demographicManager.linkDemographicToRemoteDemographic(loggedInInfo, demographicNo, remoteFacilityId, remoteDemographicNo);
        if (success) {
            updateAttachedIntegratedDemographic(loggedInInfo, messageId, demographicNo, remoteFacilityId);
        }
        return success;
    }

}
