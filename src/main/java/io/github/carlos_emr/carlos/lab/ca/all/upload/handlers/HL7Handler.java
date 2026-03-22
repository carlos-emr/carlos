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
 * HL7Handler
 * Upload handler
 *
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 * Generic HL7 upload handler for standard HL7 lab result files that do not match
 * a vendor-specific handler. Parses flat files with MSH-delimited messages and
 * routes them through {@link MessageUploader}.
 *
 * @since 2007-01-18
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
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
                MessageUploader.routeReport(loggedInInfo, "", "HL7", msg, fileId); //Setting serviceName="" for now

            }

            // Since the gdml labs show more than one lab on the same page when
            // grouped
            // by accession number their abnormal status must be updated to
            // reflect the
            // other labs that they are grouped with aswell
            updateLabStatus(messages.size());
            logger.info("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

    // recheck the abnormal status of the last 'n' labs
    private void updateLabStatus(int n) throws SQLException {
        String sql = "SELECT lab_no, result_status FROM hl7TextInfo ORDER BY lab_no DESC";
        Connection c = DbConnectionFilter.getThreadLocalDbConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery(sql);
        while (rs.next() && n > 0) {

            // only recheck the result status if it is not already set to
            // abnormal
            if (!Misc.getString(rs, "result_status").equals("A")) {

                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler h = Factory.getHandler(Misc.getString(rs, "lab_no"));
                int i = 0;
                int j = 0;
                String resultStatus = "";
                while (resultStatus.equals("") && i < h.getOBRCount()) {
                    j = 0;
                    while (resultStatus.equals("") && j < h.getOBXCount(i)) {
                        logger.info("obr(" + i + ") obx(" + j + ") abnormal ? : " + h.getOBXAbnormalFlag(i, j));
                        if (h.isOBXAbnormal(i, j)) {
                            resultStatus = "A";
                            sql = "UPDATE LOW_PRIORITY hl7TextInfo SET result_status='A' WHERE lab_no='"
                                    + Misc.getString(rs, "lab_no") + "'";
                            Misc.getString(sql);
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
