/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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
 * GDMLHandler.java
 *
 * Created on May 23, 2007, 4:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.OscarAuditLogger;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 * Upload handler for True North laboratory result files.
 *
 * @since 2007-01-18
 */
public class TRUENORTHHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);

    @Override
    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {

        int i = 0;
        try {
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
                if (isDuplicate(loggedInInfo, msg)) {
                    return ("success");
                }
                MessageUploader.routeReport(loggedInInfo, serviceName, "TRUENORTH", msg, fileId);

            }


            logger.info("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

    private boolean isDuplicate(LoggedInInfo loggedInInfo, String msg) {
        //OLIS requirements - need to see if this is a duplicate
        io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler h = Factory.getHandler("TRUENORTH", msg);
        //if final
        if (h.getOrderStatus().equals("F")) {
            String fullAcc = h.getAccessionNum();
            String acc = h.getAccessionNum();
            if (acc.indexOf("-") != -1) {
                acc = acc.substring(acc.indexOf("-") + 1);
            }
            //do we have this?
            List<Hl7TextInfo> dupResults = hl7TextInfoDao.searchByAccessionNumber(acc);
            for (Hl7TextInfo dupResult : dupResults) {
                if (dupResult.equals(fullAcc)) {
                    //if(h.getHealthNum().equals(dupResult.getHealthNumber())) {
                    OscarAuditLogger.getInstance().log(loggedInInfo, "Lab", "Skip", "Duplicate lab skipped - accession " + fullAcc + "\n" + msg);
                    return true;
                    //}
                }
                if (dupResult.getAccessionNumber().length() > 4 && dupResult.getAccessionNumber().substring(4).equals(acc)) {
                    //if(h.getHealthNum().equals(dupResult.getHealthNumber())) {
                    OscarAuditLogger.getInstance().log(loggedInInfo, "Lab", "Skip", "Duplicate lab skipped - accession " + fullAcc + "\n" + msg);
                    return true;
                    //}
                }
            }
        }
        return false;
    }

}
