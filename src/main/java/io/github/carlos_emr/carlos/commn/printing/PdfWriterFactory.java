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
 */
package io.github.carlos_emr.carlos.commn.printing;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.carlos_emr.carlos.casemgmt.service.PageNumberStamper;
import io.github.carlos_emr.carlos.casemgmt.service.PromoTextStamper;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.OscarProperties;

public class PdfWriterFactory {

    private static String confidentialtyStatement = OscarProperties.getConfidentialityStatement();
    private static String promoText = OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT");

    /**
     * @param pdfContentByte
     * @param settings
     * @return
     * @Deprecated: use the newer Itext PDF method with the same signature.
     */
    @Deprecated
    public static com.lowagie.text.pdf.PdfContentByte setFont(com.lowagie.text.pdf.PdfContentByte pdfContentByte, FontSettings settings) {
        try {
            com.lowagie.text.pdf.BaseFont baseFont = com.lowagie.text.pdf.BaseFont.createFont(settings.getFont(), settings.getCodePage(), settings.isEmbedded());
            pdfContentByte.setFontAndSize(baseFont, settings.getFontSize());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed creation of PDF Base Font ", e);
        }
        return pdfContentByte;
    }

    /**
     * @param document
     * @param stream
     * @param settings
     * @return
     * @Deprecated: use the newer Itext PDF method with the same signature.
     */
    @Deprecated
    public static com.lowagie.text.pdf.PdfWriter newInstance(com.lowagie.text.Document document, OutputStream stream, FontSettings settings) {
        com.lowagie.text.pdf.PdfWriter result;
        try {
            result = com.lowagie.text.pdf.PdfWriter.getInstance(document, stream);
        } catch (com.lowagie.text.DocumentException e) {
            MiscUtils.getLogger().error("Unable to create new PdfWriter instance", e);
            return null;
        }

//		String confidentialtyStatement = OscarProperties.getConfidentialityStatement();
//		PromoTextStamper pts = new PromoTextStamper(confidentialtyStatement, 30);
//		pts.setFontSize(settings.getFontSize());
//		result.setPageEvent(pts);
//
//		SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
//		String promoText = OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") + " " + f.format(new Date());
//		pts = new PromoTextStamper(promoText, 20);
//		pts.setFontSize(settings.getFontSize());
//		result.setPageEvent(pts);

//		PageNumberStamper pns = new PageNumberStamper(10);
//		pns.setFontSize(settings.getFontSize());
//		result.setPageEvent(pns);

        return result;
    }

    /**
     * Creates a new instance of the PDF writer.
     *
     * @param document
     * @param stream
     * @param settings
     * @return PdfWriter
     */
    public static com.itextpdf.text.pdf.PdfWriter newInstance(com.itextpdf.text.Document document, OutputStream stream, FontSettings settings) {
        com.itextpdf.text.pdf.PdfWriter result;
        try {
            result = com.itextpdf.text.pdf.PdfWriter.getInstance(document, stream);
        } catch (com.itextpdf.text.DocumentException e) {
            MiscUtils.getLogger().error("Unable to create new PdfWriter instance", e);
            return null;
        }
        PromoTextStamper pts;

        if (confidentialtyStatement != null && !confidentialtyStatement.isEmpty()) {
            pts = new PromoTextStamper(confidentialtyStatement, 30);
            pts.setFontSize(settings.getFontSize());
            result.setPageEvent(pts);
        }
        if (promoText != null && !promoText.isEmpty()) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String promoTextDate = promoText + " " + simpleDateFormat.format(new Date());
            pts = new PromoTextStamper(promoTextDate, 20);
            pts.setFontSize(settings.getFontSize());
            result.setPageEvent(pts);
        }

        PageNumberStamper pns = new PageNumberStamper(10);
        pns.setFontSize(settings.getFontSize());
        result.setPageEvent(pns);

        return result;
    }

}
