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
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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

}
