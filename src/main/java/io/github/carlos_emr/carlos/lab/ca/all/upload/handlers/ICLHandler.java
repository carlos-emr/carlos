/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

/*
 * ICLHandler.java
 * Created on Feb. 23, 2009
 * Modified by David Daley, Indivica
 * Derived from GDMLHandler.java, by wrighd
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.util.ICLUtilities;

/*
 * @author David Daley, Ithream
 */
public class ICLHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();
    Hl7TextInfoDao hl7TextInfoDao = SpringUtils.getBean(Hl7TextInfoDao.class);

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {

        ICLUtilities u = new ICLUtilities();
        int i = 0;
        try {
            ArrayList<String> messages = u.separateMessages(fileName);
            for (i = 0; i < messages.size(); i++) {

                String msg = messages.get(i);
                MessageUploader.routeReport(loggedInInfo, serviceName, "ICL", msg, fileId);

            }

            updateLabStatus(messages.size());
        } catch (Exception e) {

            MessageUploader.clean(fileId);
            logger.error("Could not upload message", e);
            return null;
        }
        return ("success");

    }

    // recheck the abnormal status of the last 'n' labs
    private void updateLabStatus(final int abnormalLabsCountToBeRechecked) {
        Hl7TextInfoDao dao = SpringUtils.getBean(Hl7TextInfoDao.class);
        List<Hl7TextInfo> infos = dao.findAll();
        Iterator<Hl7TextInfo> it = infos.iterator();
        int n = abnormalLabsCountToBeRechecked;
        while (it.hasNext() && n > 0) {
            Hl7TextInfo info = it.next();
            // only recheck the result status if it is not already set to abnormal
            if (!"A".equals(info.getResultStatus())) {
                io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler h = Factory.getHandler("" + info.getLabNumber());
                int i = 0, j = 0;
                String resultStatus = "";
                while (resultStatus.equals("") && i < h.getOBRCount()) {
                    j = 0;
                    while (resultStatus.equals("") && j < h.getOBXCount(i)) {
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
