//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
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
 *
 */
public class HRMXMLHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();

    public HRMXMLHandler() {
        logger.info("NEW HRM XML UPLOAD HANDLER instance just instantiated. ");
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        logger.info("ABOUT TO PARSE HRM XML " + fileName);

        int i = 0;
        try {
            ArrayList<String> messages = Utilities.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
                MessageUploader.routeReport(loggedInInfo, "", "HRMXML", msg, fileId);

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
