/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
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

import java.util.ArrayList;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.olis.OLISUtils;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;

/**
 *
 */
public class OLISHL7Handler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    Hl7TextInfoDao hl7TextInfoDao = (Hl7TextInfoDao) SpringUtils.getBean(Hl7TextInfoDao.class);

    private int lastSegmentId = 0;

    public OLISHL7Handler() {
        logger.info("NEW OLISHL7Handler UPLOAD HANDLER instance just instantiated. ");
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        return parse(loggedInInfo, serviceName, fileName, fileId, false);
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, boolean routeToCurrentProvider) {
        int i = 0;
        String lastTimeStampAccessed = null;
        RouteReportResults results = new RouteReportResults();

        try {
            ArrayList<String> messages = Utilities.separateMessages(fileName);

            for (i = 0; i < messages.size(); i++) {
                String msg = messages.get(i);
                logger.info(msg);

                lastTimeStampAccessed = getLastUpdateInOLIS(msg);

                if (OLISUtils.isDuplicate(loggedInInfo, msg)) {
                    LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), "OLIS", "DUPLICATE", fileName, null);
                    continue;
                }
                MessageUploader.routeReport(loggedInInfo, serviceName, "OLIS_HL7", msg.replace("\\E\\", "\\SLASHHACK\\").replace("µ", "\\MUHACK\\").replace("\\H\\", "\\.H\\").replace("\\N\\", "\\.N\\"), fileId, results);
                if (routeToCurrentProvider) {
                    ProviderLabRouting routing = new ProviderLabRouting();
                    routing.route(results.segmentId, loggedInInfo.getLoggedInProviderNo(), DbConnectionFilter.getThreadLocalDbConnection(), "HL7");

                }
                this.lastSegmentId = results.segmentId;
            }
            logger.info("Parsed OK");
        } catch (Exception e) {
            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return lastTimeStampAccessed;
    }

    public int getLastSegmentId() {
        return this.lastSegmentId;
    }
    //TODO: check HIN
    //TODO: check # of results

    private String getLastUpdateInOLIS(String msg) {
        io.github.carlos_emr.carlos.lab.ca.all.parsers.OLISHL7Handler h = (io.github.carlos_emr.carlos.lab.ca.all.parsers.OLISHL7Handler) Factory.getHandler("OLIS_HL7", msg);
        return h.getLastUpdateInOLISUnformated();
    }
}
