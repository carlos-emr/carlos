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
package io.github.carlos_emr.carlos.commn.service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import io.github.carlos_emr.OscarProperties;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class AcceptableUseAgreementManager {
    private static Logger logger = MiscUtils.getLogger();

    private static boolean loadAttempted = false;

    private static String auaText;
    private boolean auaAvailable;
    private boolean alwaysShow;
    private static Date agreementCutoffDate;
    private Property latestProperty;

    private static PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    private static void loadAUA() {
        String path = OscarProperties.getInstance().getProperty("BASE_DOCUMENT_DIR") + File.separator + "login" + File.separator + "AcceptableUseAgreement.txt";
        try {
            File auaFile = new File(path);
            if (!auaFile.exists()) {
                loadAttempted = true;
                logger.debug("No AcceptableUseAgreement File present. disabling AcceptableUseAgreement prompt");
                auaText = null;
                return; // nothing more to do
            }

            auaText = FileUtils.readFileToString(auaFile);

        } catch (Exception e) {
            logger.error("ERROR LOADING AcceptableUseAgreement text from path " + path, e);
            auaText = null;
        } finally {
            loadAttempted = true;
        }

    }

    public static boolean hasAUA() {
        String auaProp = OscarProperties.getInstance().getProperty("show_aua");

        if (auaProp == null) {
            auaProp = "";
        }

        if (!(auaProp.equals("always") || auaProp.equals("true"))) {
            return false;
        }

        logger.debug("loadAttempted " + loadAttempted + " auaText " + auaText);

        if (!loadAttempted) {
            AcceptableUseAgreementManager.loadAUA();
        }

        if (auaText == null) {
            return false;
        }
        return true;
    }

    public boolean auaAlwaysShow() {
        String auaProp = OscarProperties.getInstance().getProperty("show_aua");

        if (auaProp == null) {
            auaProp = "";
        }

        if (auaProp.equals("always")) {
            return true;
        }

        return false;
    }

    public static String getAUAText() {
        if (!loadAttempted) {
            AcceptableUseAgreementManager.loadAUA();
        }
        return auaText;
    }

    public String getText() {
        if (!loadAttempted) {
            AcceptableUseAgreementManager.loadAUA();
        }
        return auaText;
    }

    public static Date getAgreementCutoffDate() {

        if (agreementCutoffDate != null) {
            return agreementCutoffDate;
        }

        Calendar cal = GregorianCalendar.getInstance();

        Property latestProperty = AcceptableUseAgreementManager.findLatestProperty();
        if (latestProperty == null) {  //Default to one year
            cal.add(Calendar.YEAR, -1);
            return cal.getTime();
        }

        if ("aua_valid_from".equals(latestProperty.getName())) {
            //2012-09-20 01.08.30
            SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                Date validFromTime = dateTimeFormatter.parse(latestProperty.getValue());
                cal.setTimeInMillis(validFromTime.getTime());
            } catch (Exception e) {
                logger.error("Error: parsing aua_valid_from date " + latestProperty.getName(), e);
            }
        } else {
            String val = latestProperty.getValue();
            String[] splitVal = val.split(" ");
            int duration = Integer.parseInt(splitVal[0]);
            duration = duration * -1;
            int period = Calendar.YEAR;
            if ("month".equals(splitVal[1])) {
                period = Calendar.MONTH;
            } else if ("weeks".equals(splitVal[1])) {
                period = Calendar.WEEK_OF_YEAR;
            } else if ("days".equals(splitVal[1])) {
                period = Calendar.DAY_OF_YEAR;
            }
            cal.add(period, duration);

        }
        agreementCutoffDate = cal.getTime();
        return agreementCutoffDate;
    }

    public static Property findLatestProperty() {

        List<Property> auaValidFrom = propertyDao.findByName("aua_valid_from");
        List<Property> auaValiDuration = propertyDao.findByName("aua_valid_duration");

        Property latestProperty = AcceptableUseAgreementManager.findLatestProperty(auaValidFrom, auaValiDuration);
        return latestProperty;
    }


    @SafeVarargs
    public static Property findLatestProperty(List<Property>... propArray) {
        Property returnProperty = null;
        for (List<Property> propList : propArray) {

            if (propList != null) {
                for (Property p : propList) {
                    if (returnProperty == null) {
                        returnProperty = p;
                    } else if (p != null && returnProperty.getId() < p.getId()) {
                        returnProperty = p;
                    }
                }
            }
        }
        return returnProperty;
    }

    public boolean isLoadAttempted() {
        return loadAttempted;
    }

    public boolean isAuaAvailable() {
        this.auaAvailable = AcceptableUseAgreementManager.hasAUA();
        return auaAvailable;
    }

    public void setAuaAvailable(boolean auaAvailable) {
        this.auaAvailable = auaAvailable;
    }

    public boolean isAlwaysShow() {
        this.alwaysShow = auaAlwaysShow();
        return alwaysShow;
    }

    public void setAlwaysShow(boolean alwaysShow) {
        this.alwaysShow = alwaysShow;
    }

    public Property getLatestProperty() {
        this.latestProperty = AcceptableUseAgreementManager.findLatestProperty();
        return latestProperty;
    }

    public void setLatestProperty(Property latestProperty) {
        this.latestProperty = latestProperty;
    }


}