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
 * MDSHandler.java
 *
 * Created on May 23, 2007, 4:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 * Upload handler for MDS (Medical Diagnostic Services) laboratory result files.
 *
 * @since 2007-01-18
 */
public class MDSHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);

    private Integer labNo = null;

	@Override
    public Integer getLastLabNo() {
        return labNo;
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {

        int i = 0;
        RouteReportResults routeResults;
        try {

            StringBuilder audit = new StringBuilder();
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {
                String msg = messages.get(i);
				/*if(isDuplicate(loggedInInfo,msg)) {
					continue;
				}*/
                routeResults = new RouteReportResults();
                String auditLine = MessageUploader.routeReport(loggedInInfo, serviceName, "MDS", msg, fileId, routeResults) + "\n";

                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler msgHandler = Factory.getHandler(String.valueOf(routeResults.segmentId));
                if (msgHandler == null) {
                    MessageUploader.clean(fileId);
                    logger.error("Saved lab but could not parse base64 value");
                    return null;
                }

                labNo = routeResults.segmentId;

                audit.append(auditLine);

            }
            logger.info("Parsed OK");

            if (audit.length() == 0) {
                return ("success");
            }
            return (audit.toString());

        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not parse message", e);
            return null;
        }

    }

}
