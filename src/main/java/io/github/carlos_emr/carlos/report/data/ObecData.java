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


package io.github.carlos_emr.carlos.report.data;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.db.DBPreparedHandler;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * This classes main function ObecGenerate collects a group of patients with health insurance number for OHIP validation in the last specified date
 */
public class ObecData {
    private static Logger logger = MiscUtils.getLogger();

    //public ArrayList demoList = null;
    public String sql = "";
    public String results = null;
    public String text = null;
    public String connect = null;
    DBPreparedHandler accessDB = null;

    public ObecData() {
    }

    public String generateOBEC(String sDate, String eDate, Properties pp) {
        int count = 0;
        String retval = "";
        String filename = "";
        if (sDate == null || sDate.compareTo("") == 0) {
            sDate = null;
        }
        if (eDate == null || eDate.compareTo("") == 0) {
            eDate = null;
        }

        OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);
        for (Object[] o : dao.findAppointments(ConversionUtils.fromDateString(sDate), ConversionUtils.fromDateString(eDate))) {
            Appointment a = (Appointment) o[0];
            Demographic d = (Demographic) o[1];

            count = count + 1;
            if (count == 1) {
                retval = retval + "OBEC01" + space(d.getHin(), 10) + space(d.getVer(), 2) + "\r";
            } else {
                retval = retval + "\n" + "OBEC01" + space(d.getHin(), 10) + space(d.getVer(), 2) + "\r";
            }
        }

        if (retval.compareTo("") == 0) {
            filename = "0";
        } else {
            filename = writeFile(retval, pp);
        }

        return filename;
    }

    public static String space(String oldString, int leng) {

        String outputString = "";
        int i;
        for (i = oldString.length(); i < leng; i++) {
            outputString = outputString + " ";
        }
        outputString = oldString + outputString;
        return outputString;
    }

    public String writeFile(String value1, Properties pp) {
        String obecFilename = "";
        try {
            String oscarHome = pp.getProperty("DOCUMENT_DIR");

            String outbox = pp.getProperty("ONEDT_OUTBOX", "");
            Path outboxPath = Paths.get(outbox);
            File outboxFile = outboxPath.toFile();

            // Construct the filename
            obecFilename = "OBECE" + System.currentTimeMillis() + ".TXT";
            File srcFile = Paths.get(oscarHome, obecFilename).toFile();

            // Write the content to the file
            Files.write(srcFile.toPath(), value1.getBytes(), StandardOpenOption.CREATE);

            // Copy the file to the EDT outbox directory
            if (!outboxFile.exists()) { ActionUtils.createOnEDTOutboxDir(); }
            ActionUtils.copyFileToDirectory(srcFile, outboxFile, false, true);
        } catch (Exception e) {
            logger.error("Error writing or copying file", e);
        }
        return obecFilename;
    }
};
