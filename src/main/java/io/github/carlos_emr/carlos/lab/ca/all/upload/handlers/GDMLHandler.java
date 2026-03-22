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
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 * Upload handler for GDML (Gamma-Dynacare Medical Laboratories) result files.
 *
 * @since 2007-01-18
 */
public class GDMLHandler implements MessageHandler {

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
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
				/*if(isDuplicate(loggedInInfo, msg)) {
					continue;
				}*/

                routeResults = new RouteReportResults();
                MessageUploader.routeReport(loggedInInfo, serviceName, "GDML", msg, fileId, routeResults);

                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler msgHandler = Factory.getHandler(String.valueOf(routeResults.segmentId));

                if (msgHandler == null) {
                    MessageUploader.clean(fileId);
                    logger.error("Saved lab but could not parse base64 value");
                    return null;
                }

                labNo = routeResults.segmentId;

            }

            // Since the gdml labs show more than one lab on the same page when grouped
            // by accession number their abnormal status must be updated to reflect the
            // other labs that they are grouped with aswell
            updateLabStatus(messages.size());

            logger.debug("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

    // recheck the abnormal status of the last 'n' labs
    //this is a very weird method, and needs to be refactored or better yet, removed, and rethought out.
    private void updateLabStatus(int n) {
        Hl7TextInfoDao dao = SpringUtils.getBean(Hl7TextInfoDao.class);

        List<Hl7TextInfo> infos = dao.findAll();

        for (int k = 0; k < infos.size() && n > 0; k++) {
            Hl7TextInfo info = infos.get(k);

            // only recheck the result status if it is not already set to abnormal
            if (info.getResultStatus() == null || !info.getResultStatus().equals("A")) {
                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler h = Factory.getHandler("" + info.getLabNumber());
                int i = 0;
                int j = 0;
                String resultStatus = "";
                while (resultStatus.equals("") && i < h.getOBRCount()) {
                    j = 0;
                    while (resultStatus.equals("") && j < h.getOBXCount(i)) {
                        logger.info("obr(" + i + ") obx(" + j + ") abnormal ? : " + h.getOBXAbnormalFlag(i, j));
                        if (h.isOBXAbnormal(i, j)) {
                            resultStatus = "A";
                            Hl7TextInfo obj = hl7TextInfoDao.findLabId(info.getLabNumber());
                            if (obj != null) {
                                obj.setResultStatus("A");
                                hl7TextInfoDao.merge(obj);
                            }
                        }
                        j++;
                    }
                    i++;
                }
            }

            n--;
        }
    }

}
