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

import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.MsgDemoMap;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
     * Attach an array of local Demographic numbers to the given message id.
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
     * Gets a list of messages attached to the given demographic number
     *
     * @param loggedInInfo
     * @param demographicNo
     * @return
     */
    public List<MsgDemoMap> getMessageMapByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo);

}
