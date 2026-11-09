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
 * Modifications by CARLOS Contributors, 2026.
 */


/*
 * HL7Handler
 * Upload handler
 *
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import java.sql.SQLException;
import java.util.ArrayList;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 *
 */
public class HL7Handler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();

    public HL7Handler() {
        logger.info("NEW HL7Handler UPLOAD HANDLER instance just instantiated. ");
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        logger.info("ABOUT TO PARSE!");

        int i = 0;
        try {
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            java.util.Set<Integer> createdIds = new java.util.LinkedHashSet<>();
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
                var result = new io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults();
                MessageUploader.routeReport(loggedInInfo, "", "HL7", msg, fileId, result);
                if (result.segmentId <= 0) throw new IllegalStateException("Upload did not return a stored report identity");
                createdIds.add(result.segmentId);

            }

            // Since the gdml labs show more than one lab on the same page when
            // grouped
            // by accession number their abnormal status must be updated to
            // reflect the
            // other labs that they are grouped with aswell
            updateLabStatus(createdIds);
            logger.info("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

    // Recheck only identities returned by this upload, never globally recent rows.
    static void updateLabStatus(java.util.Set<Integer> ids) throws SQLException {
        if (ids.isEmpty()) return;
        Hl7TextInfoDao dao = SpringUtils.getBean(Hl7TextInfoDao.class);
        var messages = SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao.class);
        for (Integer id : ids) {
            if (id == null || id <= 0) throw new IllegalArgumentException("Invalid uploaded report identity");
            var rows = dao.findByLabId(id);
            if (rows.size() != 1) throw new IllegalStateException("Uploaded lab metadata is missing or ambiguous");
            var lab = rows.getFirst();
            if (lab.getResultStatus() != null && "A".equals(lab.getResultStatus().trim())) continue;
            // Database failures must propagate; the ID-based factory catches retrieval errors.
            var stored = messages.find(id.intValue());
            if (stored == null) throw new IllegalStateException("Uploaded lab content is missing");
            io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler handler = Factory.getHandler(stored);
            if (handler == null) throw new IllegalStateException("Uploaded lab content cannot be parsed for abnormal recheck");
            boolean abnormal = false;
            for (int obr = 0; !abnormal && obr < handler.getOBRCount(); obr++) {
                for (int obx = 0; !abnormal && obx < handler.getOBXCount(obr); obx++) {
                    abnormal = handler.isOBXAbnormal(obr, obx);
                }
            }
            if (abnormal) dao.updateResultStatusLowPriority(id, "A");
        }
    }

}
