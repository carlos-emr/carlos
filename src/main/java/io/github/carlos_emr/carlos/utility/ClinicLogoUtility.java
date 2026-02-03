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
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility for fetching and creating clinic logo images into PDF documents.
 */
public final class ClinicLogoUtility {

    private static final Logger logger = MiscUtils.getLogger();
    private static EctConsultationFormRequestUtil ectConsultationFormRequestUtil;
    private static final float HEADER_HEIGHT = 70f;
    private static final float LOGO_HEIGHT = 65f;

    /**
     * Create a PDF TABLE that contains the predetermined logo image set
     * in the oscar properties file as "clinicLetterheadLogo".  This logo image
     * is normally located in OscarDocuments
     *
     * @return IText PDF TABLE element for insert into a PDF
     */
    public static PdfPTable createLogoHeader() {

        PdfPTable infoTable = new PdfPTable(1);
        OscarProperties props = OscarProperties.getInstance();

        String filename = "";
        if (props.getProperty("multisites") != null && "on".equalsIgnoreCase(props.getProperty("multisites")) && ectConsultationFormRequestUtil != null) {
            DocumentDao documentDao = (DocumentDao) SpringUtils.getBean(DocumentDao.class);
            SiteDao siteDao = (SiteDao) SpringUtils.getBean(SiteDao.class);
            Site site = siteDao.getById(Integer.valueOf(ectConsultationFormRequestUtil.siteName));
            if (site != null) {
                if (site.getSiteLogoId() != null) {
                    Document d = documentDao.getDocument(String.valueOf(site.getSiteLogoId()));
                    String dir = props.getProperty("DOCUMENT_DIR");
                    filename = dir.concat(d.getDocfilename());
                } else {
                    //If no logo file uploaded for this site, use the default one defined in oscar properties file.
                    filename = props.getProperty("clinicLetterheadLogo");
                    if (filename == null) {
                        filename = props.getProperty("faxLogoInConsultation");
                    }
                }
            }
        } else {
            filename = props.getProperty("clinicLetterheadLogo");
            if (filename == null) {
                filename = props.getProperty("faxLogoInConsultation");
            }
        }

        Path path = Paths.get(filename);
        if (Files.exists(path)) {
            addImage(infoTable, filename, PageSize.LETTER.getWidth() * 0.5f, LOGO_HEIGHT);
        }
        return infoTable;

    }

    public static PdfPTable createLogoHeader(EctConsultationFormRequestUtil ectConsultationFormRequestUtil) {
        ClinicLogoUtility.ectConsultationFormRequestUtil = ectConsultationFormRequestUtil;
        return createLogoHeader();
    }

    private static void addImage(PdfPTable pdfPTable, String filename, float width, float height) {
        try (FileInputStream fileInputStream = new FileInputStream(filename)) {
            PdfPCell cell = new PdfPCell();
            byte[] faxLogImage = new byte[1024 * 256];
            fileInputStream.read(faxLogImage);
            Image image = Image.getInstance(faxLogImage);
            image.scaleToFit(width, height);
            image.setBorder(0);
            cell.addElement(image);
            cell.setPadding(-11);
            cell.setBorder(0);
            cell.setFixedHeight(HEADER_HEIGHT);
            pdfPTable.addCell(cell);
        } catch (FileNotFoundException e) {
            logger.error("Failed to locate file at " + filename, e);
        } catch (BadElementException e) {
            logger.error("Unexpected error.", e);
        } catch (MalformedURLException e) {
            logger.error("This image location is malformed " + filename, e);
        } catch (IOException e) {
            logger.error("Unexpected error.", e);
        }
    }
}
