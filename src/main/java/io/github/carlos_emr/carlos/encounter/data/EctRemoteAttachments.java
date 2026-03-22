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

package io.github.carlos_emr.carlos.encounter.data;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.dao.RemoteAttachmentsDao;
import io.github.carlos_emr.carlos.commn.model.RemoteAttachments;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Manages remote attachments associated with a patient demographic.
 * Retrieves message IDs, senders, and dates for remote attachments,
 * and provides lookup of the originating location for each message.
 *
 * @since 2001-01-01
 */
public class EctRemoteAttachments {

    String demoNo;
    public ArrayList<String> messageIds;
    public ArrayList<String> savedBys;
    public ArrayList<String> dates;

    /**
     * Default constructor. Initializes all fields to null.
     */
    public EctRemoteAttachments() {
        demoNo = null;
        messageIds = null;
        savedBys = null;
        dates = null;
    }

    /**
     * Populates message IDs, saved-by names, and dates for the given demographic.
     *
     * @param demo String the demographic number
     */
    public void estMessageIds(String demo) {
        demoNo = demo;
        messageIds = new ArrayList<String>();
        savedBys = new ArrayList<String>();
        dates = new ArrayList<String>();

        RemoteAttachmentsDao dao = SpringUtils.getBean(RemoteAttachmentsDao.class);
        for (RemoteAttachments ra : dao.findByDemoNo(ConversionUtils.fromIntString(demoNo))) {
            dates.add(ConversionUtils.toDateString(ra.getDate()));
            messageIds.add("" + ra.getMessageId());
            savedBys.add(ra.getSavedBy());
        }
    }

    /**
     * Retrieves the originating location description and subject for a message.
     *
     * @param messId String the message ID to look up
     * @return ArrayList containing [subject, locationDescription] pairs
     */
    public ArrayList<String> getFromLocation(String messId) {
        ArrayList<String> retval = new ArrayList<String>();

        OscarCommLocationsDao dao = SpringUtils.getBean(OscarCommLocationsDao.class);
        for (Object[] o : dao.findFormLocationByMesssageId(messId)) {
            String locationDesc = String.valueOf(o[0]);
            String thesubject = String.valueOf(o[1]);

            retval.add(thesubject);
            retval.add(locationDesc);
        }

        return retval;
    }
}
