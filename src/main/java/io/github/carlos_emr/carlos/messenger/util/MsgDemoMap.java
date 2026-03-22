/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/*
 * MsgDemoMap.java
 *
 * Created on April 24, 2005, 3:47 PM
 */

package io.github.carlos_emr.carlos.messenger.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Manages the mapping between messages and patient demographics in the messaging system.
 * This class provides functionality to link messages to specific patients, retrieve
 * message-demographic mappings, and manage these associations.
 * 
 * @deprecated Use the commons entity at io.github.carlos_emr.carlos.commn.model.MsgDemoMap
 * @since 2003
 */
@Deprecated
/**
 * Utility for mapping demographic numbers to patient names in the messenger module.
 *
 * @since 2001-01-01
 */
public class MsgDemoMap {

    /** Data Access Object for message-demographic mapping operations */
    private MsgDemoMapDao dao = SpringUtils.getBean(MsgDemoMapDao.class);

    /**
     * Default constructor initializes the MsgDemoMap utility.
     * 
     * @since 2003
     */
    public MsgDemoMap() {
    }

    /**
     * Creates a new link between a message and a patient demographic record.
     * This establishes the association that allows messages to be grouped by patient.
     * 
     * @param msgId String representation of the message ID to link
     * @param demographic_no String representation of the patient demographic number
     * @since 2003
     */
    public void linkMsg2Demo(String msgId, String demographic_no) {
        // Create a new mapping entity and set the message and demographic IDs
        io.github.carlos_emr.carlos.commn.model.MsgDemoMap mdm = new io.github.carlos_emr.carlos.commn.model.MsgDemoMap();
        mdm.setMessageID(Integer.valueOf(msgId));
        mdm.setDemographic_no(Integer.valueOf(demographic_no));

        // Persist the mapping to the database
        dao.persist(mdm);
    }

    /**
     * Retrieves a mapping of demographic numbers to formatted patient names for a specific message.
     * Returns a HashMap where keys are demographic numbers and values are lists of formatted names.
     * 
     * @param msgId String representation of the message ID to look up
     * @return HashMap&lt;String, List&lt;String&gt;&gt; mapping of demographic numbers to patient name lists
     * @since 2003
     */
    public HashMap<String, List<String>> getDemoMap2(String msgId) {
        HashMap<String, List<String>> demoMap = new HashMap<String, List<String>>();
        List<String> list = null;

        // Retrieve message-demographic mappings and associated patient data
        MsgDemoMapDao dao = SpringUtils.getBean(MsgDemoMapDao.class);
        for (Object[] o : dao.getMessagesAndDemographicsByMessageId(ConversionUtils.fromIntString(msgId))) {
            io.github.carlos_emr.carlos.commn.model.MsgDemoMap m = (io.github.carlos_emr.carlos.commn.model.MsgDemoMap) o[0];
            Demographic d = (Demographic) o[1];
            
            // Add formatted name to existing demographic entry or create new entry
            if (demoMap.containsKey(String.valueOf(d.getDemographicNo()))) {
                demoMap.get(String.valueOf(d.getDemographicNo())).add(d.getFormattedName());
            } else {
                list = new ArrayList<String>();
                list.add(d.getFormattedName());
                demoMap.put(String.valueOf(d.getDemographicNo()), list);
            }
        }

        return demoMap;
    }


    /**
     * Retrieves a Hashtable mapping demographic numbers to full patient names for a specific message.
     * This method provides a legacy interface using Hashtable instead of HashMap.
     * 
     * @param msgId String representation of the message ID to look up
     * @return Hashtable mapping demographic numbers to full patient names
     * @since 2003
     */
    public Hashtable getDemoMap(String msgId) {
        Hashtable demoMap = new Hashtable();

        // Retrieve message-demographic mappings and build hashtable of patient names
        MsgDemoMapDao dao = SpringUtils.getBean(MsgDemoMapDao.class);
        for (Object[] o : dao.getMessagesAndDemographicsByMessageId(ConversionUtils.fromIntString(msgId))) {
            io.github.carlos_emr.carlos.commn.model.MsgDemoMap m = (io.github.carlos_emr.carlos.commn.model.MsgDemoMap) o[0];
            Demographic d = (Demographic) o[1];
            demoMap.put("" + d.getDemographicNo(), d.getFullName());
        }

        return demoMap;
    }

    /**
     * Retrieves a list of message IDs associated with a specific patient demographic number.
     * This method returns all messages linked to the given patient.
     * 
     * @param demographic_no String representation of the patient demographic number
     * @return List&lt;String&gt; list of message IDs associated with the patient
     * @since 2003
     */
    public List<String> getMsgVector(String demographic_no) {
        List<String> msgVector = new ArrayList<String>();
        
        // Retrieve all message mappings for the specified patient
        MsgDemoMapDao dao = SpringUtils.getBean(MsgDemoMapDao.class);
        for (Object[] o : dao.getMapAndMessagesByDemographicNo(ConversionUtils.fromIntString(demographic_no))) {
            io.github.carlos_emr.carlos.commn.model.MsgDemoMap map = (io.github.carlos_emr.carlos.commn.model.MsgDemoMap) o[0];
            msgVector.add("" + map.getMessageID());
        }
        return msgVector;
    }

    /**
     * Retrieves a list of message IDs associated with a specific patient and message type.
     * This method filters messages by both demographic number and message type.
     * 
     * @param demographic_no String representation of the patient demographic number
     * @param type Integer representing the message type to filter by
     * @return List&lt;String&gt; list of message IDs matching the patient and type criteria
     * @since 2003
     */
    public List<String> getMsgList(String demographic_no, Integer type) {
        List<String> msgList = new ArrayList<String>();
        
        // Retrieve message mappings filtered by patient and message type
        MsgDemoMapDao dao = SpringUtils.getBean(MsgDemoMapDao.class);
        for (Object[] o : dao.getMapAndMessagesByDemographicNoAndType(Integer.valueOf(demographic_no), type)) {
            io.github.carlos_emr.carlos.commn.model.MsgDemoMap map = (io.github.carlos_emr.carlos.commn.model.MsgDemoMap) o[0];
            msgList.add("" + map.getMessageID());
        }
        return msgList;
    }

    /**
     * Removes the link between a message and a patient demographic record.
     * This method deletes the mapping between the specified message and patient.
     * 
     * @param demographic_no String representation of the patient demographic number
     * @param messageID String representation of the message ID to unlink
     * @since 2003
     */
    public void unlinkMsg(String demographic_no, String messageID) {
        MiscUtils.getLogger().debug("input msgId: " + messageID + "  input demographic_no: " + demographic_no);

        // Remove the mapping from the database
        dao.remove(Integer.parseInt(messageID), Integer.parseInt(demographic_no));
    }
}
