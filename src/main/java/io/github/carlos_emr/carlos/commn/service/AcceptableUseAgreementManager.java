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

import io.github.carlos_emr.CarlosProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.nio.charset.StandardCharsets;

public class AcceptableUseAgreementManager {
    private static Logger logger = MiscUtils.getLogger();

    private static volatile boolean loadAttempted = false;

    private static String auaText;
    private static AgreementVersion loadedVersion;

    private record AgreementVersion(Path path, FileTime modified, long size, Object fileKey) { }

    private boolean auaAvailable;
    private boolean alwaysShow;
    private static Date agreementCutoffDate;
    private Property latestProperty;

    private static PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    /**
     * Returns the canonical agreement used by both upload and login readers.
     * The directory may be created by an administrator upload.
     * @return a validated child beneath BASE_DOCUMENT_DIR/login
     * @since 2026-09-19
     */
    public static File getAgreementFile() {
        File base = PathValidationUtils.validateConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR"), "BASE_DOCUMENT_DIR");
        File directory = PathValidationUtils.validateGeneratedChildPath("login", base);
        return PathValidationUtils.validateGeneratedChildPath("AcceptableUseAgreement.txt", directory);
    }

    /**
     * Discards cached text and validity after a successful administrator change.
     * Uploading text does not change the show_aua login policy.
     * @since 2026-09-19
     */
    public static synchronized void invalidateCache() {
        loadAttempted = false;
        auaText = null;
        loadedVersion = null;
        agreementCutoffDate = null;
    }

    private static synchronized void loadAUA() {
        try {
            Path path = getAgreementFile().toPath();
            boolean exists = Files.exists(path);
            BasicFileAttributes attributes = exists ? Files.readAttributes(path, BasicFileAttributes.class) : null;
            AgreementVersion version = new AgreementVersion(path,
                    exists ? attributes.lastModifiedTime() : null,
                    exists ? attributes.size() : -1,
                    exists ? attributes.fileKey() : null);
            if (loadAttempted && version.equals(loadedVersion)) return;
            // Also notice deployment/restoration changes outside the upload action.
            // A missing file must never leave an old agreement active in memory.
            auaText = exists ? Files.readString(path, StandardCharsets.UTF_8) : null;
            loadedVersion = version;
        } catch (Exception e) {
            logger.error("Error loading acceptable-use agreement", e);
            auaText = null;
            loadedVersion = null;
        } finally {
            loadAttempted = true;
        }
    }

    public static synchronized boolean hasAUA() {
        String auaProp = CarlosProperties.getInstance().getProperty("show_aua");

        if (auaProp == null) {
            auaProp = "";
        }

        if (!(auaProp.equals("always") || auaProp.equals("true"))) {
            return false;
        }

        logger.debug("Acceptable-use agreement load attempted: {}", loadAttempted);

        AcceptableUseAgreementManager.loadAUA();

        if (auaText == null) {
            return false;
        }
        return true;
    }

    public boolean auaAlwaysShow() {
        String auaProp = CarlosProperties.getInstance().getProperty("show_aua");

        if (auaProp == null) {
            auaProp = "";
        }

        if (auaProp.equals("always")) {
            return true;
        }

        return false;
    }

    public static synchronized String getAUAText() {
        AcceptableUseAgreementManager.loadAUA();
        return auaText;
    }

    public String getText() {
        return getAUAText();
    }

    public static synchronized Date getAgreementCutoffDate() {

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