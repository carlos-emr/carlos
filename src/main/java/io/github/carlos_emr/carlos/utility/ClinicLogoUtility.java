/**
 * Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
 * The Pharmacists Clinic
 * Faculty of Pharmaceutical Sciences
 * University of British Columbia
 * Vancouver, British Columbia, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.commn.model.Document;
import org.openpdf.text.BadElementException;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility for loading clinic logo images and embedding them into PDF document headers.
 *
 * <p>Resolves the clinic logo from multiple sources in order of priority:</p>
 * <ol>
 *   <li>Multi-site configuration: site-specific logo from the document database</li>
 *   <li>Property {@code clinicLetterheadLogo}: file path to a letterhead logo image</li>
 *   <li>Property {@code faxLogoInConsultation}: fallback logo file path</li>
 * </ol>
 *
 * <p>Creates an OpenPDF {@link PdfPTable} containing the scaled logo image, suitable for
 * insertion into fax cover pages, consultation letters, and other PDF documents.</p>
 *
 * @see io.github.carlos_emr.carlos.fax.util.PdfCoverPageCreator
 * @since 2022-04-21
 */
public final class ClinicLogoUtility {

    private static final Logger logger = MiscUtils.getLogger();
    private static final float HEADER_HEIGHT = 70f;
    private static final float LOGO_HEIGHT = 65f;

    /**
     * Creates a PDF table containing the clinic logo image configured in CARLOS properties.
     *
     * <p>This no-context entry point always uses the configured global
     * {@code clinicLetterheadLogo} / {@code faxLogoInConsultation} default.
     * Explicit site calls never leave request context behind for a fax cover.</p>
     *
     * @return PdfPTable an OpenPDF table element containing the logo image, for insertion into a PDF
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static PdfPTable createLogoHeader() {
        return logoTable(configuredLogo(CarlosProperties.getInstance(), false));
    }

    /**
     * Creates a logo header table using the site context from a consultation form request.
     *
     * <p>This compatibility overload accepts the historical numeric site identifier.
     * Its request context is local to this call and never affects later global headers.
     * Name-valued consultation records use {@link #resolveConsultationLogo(String)}.</p>
     *
     * @param ectConsultationFormRequestUtil EctConsultationFormRequestUtil providing site context
     * @return PdfPTable the logo header table
     */
    public static PdfPTable createLogoHeader(EctConsultationFormRequestUtil ectConsultationFormRequestUtil) {
        CarlosProperties props = CarlosProperties.getInstance();
        String siteId = ectConsultationFormRequestUtil == null ? null : ectConsultationFormRequestUtil.siteName;
        File logo = siteLogo(props, siteId, false);
        return logoTable(logo == null ? configuredLogo(props, false) : logo);
    }

    /**
     * Resolves a consultation's stored site name without changing its PDF layout.
     * The current form stores names; a numeric value is a legacy ID only when
     * no exact name exists. A numeric name must not change ID-only callers.
     * Consultation headers retain their faxLogoInConsultation-only fallback.
     */
    public static File resolveConsultationLogo(String siteName) {
        CarlosProperties props = CarlosProperties.getInstance();
        File logo = siteLogo(props, siteName, true);
        return logo == null ? configuredLogo(props, true) : logo;
    }

    private static PdfPTable logoTable(File logo) {
        PdfPTable table = new PdfPTable(1);
        if (logo != null) {
            addImage(table, logo, PageSize.LETTER.getWidth() * 0.5f, LOGO_HEIGHT);
        }
        return table;
    }

    private static File configuredLogo(CarlosProperties props, boolean consultation) {
        String filename = consultation ? props.getProperty("faxLogoInConsultation") : props.getProperty("clinicLetterheadLogo");
        if (!consultation && filename == null) {
            filename = props.getProperty("faxLogoInConsultation");
        }
        if (filename == null || filename.isBlank()) {
            return null;
        }
        try {
            File file = PathValidationUtils.resolveTrustedPath(new File(filename));
            if (Files.isRegularFile(file.toPath()) && Files.isReadable(file.toPath())) {
                return file;
            }
        } catch (SecurityException e) {
            logger.warn("Skipping clinic logo: invalid configured logo path");
            return null;
        }
        logger.warn("Skipping clinic logo: configured logo is unavailable");
        return null;
    }

    private static File siteLogo(CarlosProperties props, String reference, boolean nameFirst) {
        if (!"on".equalsIgnoreCase(props.getProperty("multisites")) || reference == null || reference.isBlank()) {
            return null;
        }
        SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
        Site site = null;
        if (nameFirst) {
            // Do not use findByName: its case-insensitive DB lookup and swallowed
            // duplicate/query exceptions cannot prove an exact unique name.
            List<Site> allSites = siteDao.getAllSites();
            if (allSites == null) {
                logger.warn("Using configured clinic logo: site lookup is unavailable");
                return null;
            }
            for (Site candidate : allSites) {
                if (candidate != null && reference.equals(candidate.getName())) {
                    if (site != null) {
                        logger.warn("Using configured clinic logo: requested site name is ambiguous");
                        return null;
                    }
                    site = candidate;
                }
            }
        }
        if (site == null) {
            Integer id = legacySiteId(reference);
            if (id != null) {
                site = siteDao.getById(id);
            }
        }
        if (site == null) {
            logger.warn("Using configured clinic logo: requested site is unavailable");
            return null;
        }
        if (site.getSiteLogoId() == null) {
            return null;
        }
        DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
        Document document = documentDao.getDocument(String.valueOf(site.getSiteLogoId()));
        if (document == null || document.getDocfilename() == null || document.getDocfilename().isBlank()) {
            logger.warn("Using configured clinic logo: site logo metadata is unavailable");
            return null;
        }
        try {
            File directory = PathValidationUtils.resolveConfiguredDirectory(props.getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
            File logo = PathValidationUtils.resolveTrustedPath(new File(directory, document.getDocfilename()));
            // Validate and then USE the same canonical file: a DB path or a
            // symlink must not read outside the configured document store.
            PathValidationUtils.validateExistingPath(logo, directory);
            if (Files.isRegularFile(logo.toPath()) && Files.isReadable(logo.toPath())) {
                return logo;
            }
        } catch (SecurityException e) {
            // The containment helper retains its security diagnostic. Do not
            // append clinical filenames, site text or causes to this warning.
            logger.warn("Using configured clinic logo: invalid site logo path");
            return null;
        }
        logger.warn("Using configured clinic logo: site logo file is unavailable");
        return null;
    }

    private static Integer legacySiteId(String reference) {
        try {
            // Preserve Integer.valueOf's positive legacy forms, including +2
            // and 002; malformed/overflow/absent references have no site.
            int id = Integer.parseInt(reference);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Loads an image file and adds it as a scaled cell to the given PDF table.
     *
     * @param pdfPTable PdfPTable the table to add the image cell to
     * @param logoFile File the validated image file
     * @param width float the maximum width to scale the image to (in points)
     * @param height float the maximum height to scale the image to (in points)
     */
    // FindSecBugs CRLF_INJECTION_LOGS: logoFile is resolved via PathValidationUtils.resolveTrustedPath from server-configured properties (clinicLetterheadLogo / faxLogoInConsultation / DOCUMENT_DIR + DB-stored document filename); no request-controlled string reaches this log.
    @SuppressFBWarnings(value = "CRLF_INJECTION_LOGS", justification = "logoFile path is derived from server config properties and a server-stored document filename, not from request input; no attacker-controlled CR/LF.")
    private static void addImage(PdfPTable pdfPTable, File logoFile, float width, float height) {
        try (FileInputStream fileInputStream = new FileInputStream(logoFile)) {
            PdfPCell cell = new PdfPCell();
            byte[] faxLogImage = fileInputStream.readAllBytes();
            Image image = Image.getInstance(faxLogImage);
            image.scaleToFit(width, height);
            image.setBorder(0);
            cell.addElement(image);
            cell.setPadding(-11);
            cell.setBorder(0);
            cell.setFixedHeight(HEADER_HEIGHT);
            pdfPTable.addCell(cell);
        } catch (FileNotFoundException e) {
            logger.error("Failed to locate file at " + logoFile, e);
        } catch (BadElementException e) {
            logger.error("Unexpected error.", e);
        } catch (MalformedURLException e) {
            logger.error("This image location is malformed " + logoFile, e);
        } catch (IOException e) {
            logger.error("Unexpected error.", e);
        }
    }
}
