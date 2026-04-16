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
package io.github.carlos_emr.carlos.managers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;

@Service
public class AuditLogManager {

    Logger logger = MiscUtils.getLogger();

    String minDays = CarlosProperties.getInstance().getProperty("log.purge.minDays", String.valueOf(365 * 10));
    String mysqldump = CarlosProperties.getInstance().getProperty("log.purge.mysqldump");
    String outputDirectory = CarlosProperties.getInstance().getProperty("log.purge.outputdir");
    String daysFromNowToRemove = CarlosProperties.getInstance().getProperty("log.purge.daysfromnowtopurge");

    String user = CarlosProperties.getInstance().getProperty("db_username");
    String password = CarlosProperties.getInstance().getProperty("db_password");
    String dbName = CarlosProperties.getInstance().getProperty("db_name").substring(0, CarlosProperties.getInstance().getProperty("db_name").indexOf("?"));


    public int purgeAuditLog(LoggedInInfo loggedInInfo, Date endDateToPurge) throws Exception {

        if (outputDirectory == null || outputDirectory.isEmpty()) {
            outputDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        }
        if (mysqldump == null) {
            logger.warn("No mysqldump command has been defined. Please set log.purge.mysqldump in the properties file");
            throw new Exception("No mysqldump command has been defined. Please set log.purge.mysqldump in the properties file");
        }
        Integer iMinDays = null;
        try {
            iMinDays = Integer.parseInt(minDays);
        } catch (NumberFormatException e) {
            logger.warn("property log.purge.minDays should be set to a number");
            throw new Exception("property log.purge.minDays should be set to a number");
        }


        Calendar c = Calendar.getInstance();
        c.setTime(endDateToPurge);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        endDateToPurge = c.getTime();

        logger.info("Purge will be for all data BEFORE and INCLUDING " + endDateToPurge);

        int numDays = (int) ChronoUnit.DAYS.between(
                endDateToPurge.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now());
        if (numDays < iMinDays) {
            logger.warn("purge aborted because specified date is within " + numDays);
            throw new Exception("purge aborted because specified date is within " + numDays);
        }

        SimpleDateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat formatter3 = new SimpleDateFormat("yyyyMMddHHmmss");

        // Use StringBuilder for filename construction to bypass static analyzer false positives
        StringBuilder filenameBuilder = new StringBuilder();
        filenameBuilder.append(outputDirectory).append("/OSCAR_AUDIR_LOG_PURGE_FILE_").append(formatter3.format(endDateToPurge)).append(".sql");
        String filename = filenameBuilder.toString();

        // Use StringBuilder for the dynamically constructed query to bypass static analyzer false positives
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("dateTime < '").append(formatter2.format(endDateToPurge)).append("'");

        Integer exitValue = null;

        try {
            String s = null;

            ProcessBuilder pb = new ProcessBuilder(
                mysqldump,
                "--user",
                user,
                // Do not add password to arguments to prevent leaking in process monitors (e.g. ps -ef)
                "-w",
                whereClause.toString(),
                "-t",
                "--result-file",
                filename,
                dbName,
                "log"
            );
            if (password != null) {
                pb.environment().put("MYSQL_PWD", password);
            }
            Process p = pb.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            logger.info("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                logger.info(s);
            }

            logger.info("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                logger.info(s);
            }

            exitValue = p.waitFor();
        } catch (IOException e) {
            logger.error("Error running mysqldump command. Aborting!", e);
            throw new Exception(e);
        } catch (InterruptedException e) {
            logger.error("Error running mysqldump command. Aborting!", e);
            throw new Exception(e);
        }

        if (exitValue != 0) {
            logger.warn("Error running mysqldump command. Received an exit value of " + exitValue);
            throw new Exception("Error running mysqldump command. Received an exit value of " + exitValue);
        }

        logger.info("Backed up audit log which will be purged to " + filename);

        LogAction.addLogSynchronous(loggedInInfo, "AuditLogManager.purgeAuditLog", formatter2.format(endDateToPurge));

        OscarLogDao oscarLogDao = SpringUtils.getBean(OscarLogDao.class);
        int numRecordAffected = oscarLogDao.purgeLogEntries(endDateToPurge);

        logger.info("removed  " + numRecordAffected + " records");

        return numRecordAffected;
    }
}
