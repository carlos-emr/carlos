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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

public class AcceptableUseAgreementManager {
    private static Logger logger = MiscUtils.getLogger();

    // Coordinate agreement text, version and validity updates on one private monitor.
    private static final Object CACHE_LOCK = new Object();

    private static volatile boolean loadAttempted = false;

    private static String auaText;
    // Display form of auaText, sanitized once per load rather than on every login page.
    private static String auaHtml;
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
    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            loadAttempted = false;
            auaText = null;
            auaHtml = null;
            loadedVersion = null;
            agreementCutoffDate = null;
        }
    }

    /**
     * Keeps agreement readers out while an administrator publishes text and
     * persists its validity, including any rollback after a persistence error.
     * The update may call the cache methods reentrantly without exposing the monitor.
     *
     * @param update the complete publication, validity update and rollback operation
     */
    public static void updateAgreement(Runnable update) {
        synchronized (CACHE_LOCK) {
            update.run();
        }
    }

    private static void loadAUA() {
        synchronized (CACHE_LOCK) {
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
                auaHtml = auaText == null ? null : sanitizeForDisplay(auaText);
                loadedVersion = version;
            } catch (Exception e) {
                logger.error("Error loading acceptable-use agreement", e);
                auaText = null;
                auaHtml = null;
                loadedVersion = null;
            } finally {
                loadAttempted = true;
            }
        }
    }

    public static boolean hasAUA() {
        synchronized (CACHE_LOCK) {
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

    public static String getAUAText() {
        synchronized (CACHE_LOCK) {
            AcceptableUseAgreementManager.loadAUA();
            return auaText;
        }
    }

    public String getText() {
        return getAUAText();
    }

    /**
     * Returns the agreement as HTML that is safe to write into a page unencoded.
     *
     * <p>The file has always been rendered as markup, and deployed agreements use document
     * formatting (headings, paragraphs, emphasis, lists, tables, links), which is kept. Since
     * an administrator can publish the file from Administration and the login page shows it
     * before anyone signs in, everything else -- script, event handlers, style attributes,
     * frames, forms, images -- is removed so an {@code _admin} account cannot run script or
     * restyle the page in front of every user's credentials. The stored file and
     * {@link #getAUAText()} keep the exact uploaded bytes.
     *
     * @return sanitized agreement HTML, or {@code null} when no agreement can be read
     * @since 2026-10-22
     */
    public static String getAUAHtml() {
        synchronized (CACHE_LOCK) {
            AcceptableUseAgreementManager.loadAUA();
            return auaHtml;
        }
    }

    /**
     * Bean accessor for the login page.
     *
     * @return the same value as {@link #getAUAHtml()}
     * @since 2026-10-22
     */
    public String getHtml() {
        return getAUAHtml();
    }

    // Document formatting only: relaxed() carries no style attribute, script, handler or frame;
    // images are dropped too, so the login page cannot be made to load or show anything else.
    private static final Safelist AGREEMENT_SAFELIST = Safelist.relaxed().removeTags("img");

    static String sanitizeForDisplay(String text) {
        // prettyPrint off: keep the author's whitespace instead of reflowing it.
        return Jsoup.clean(text, "", AGREEMENT_SAFELIST, new Document.OutputSettings().prettyPrint(false));
    }

    public static Date getAgreementCutoffDate() {
        synchronized (CACHE_LOCK) {

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