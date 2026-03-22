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
 * Upload handler for CLS (Calgary Laboratory Services) result files. Parses CLS
 * HL7 messages with version tracking and routes them through {@link MessageUploader}.
 *
 * @since 2007-01-18
 */
public class CLSHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);


    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {

        int i = 0;
        RouteReportResults routeResults;
        try {
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
				/*
				if(isDuplicate(msg)) {
					return ("success");
				}
				*/

                routeResults = new RouteReportResults();
                MessageUploader.routeReport(loggedInInfo, serviceName, "CLS", msg, fileId, routeResults);

                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler msgHandler = Factory.getHandler(String.valueOf(routeResults.segmentId));

                if (msgHandler == null) {
                    MessageUploader.clean(fileId);
                    logger.error("Saved lab but could not parse base64 value");
                    return null;
                }

            }

            logger.info("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

}
