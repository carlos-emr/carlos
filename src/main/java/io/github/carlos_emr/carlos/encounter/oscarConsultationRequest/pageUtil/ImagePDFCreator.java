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

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
import com.itextpdf.text.pdf.codec.TiffImage;

public class ImagePDFCreator extends PdfPageEventHelper {

    private static Logger logger = MiscUtils.getLogger();
    private OutputStream os;
    private String imagePath;
    private String imageTitle;
    private Document document;

    /**
     * Prepares a ConsultationPDFCreator instance to print a consultation request to PDF.
     *
     * @param request contains the information necessary to construct the consultation request
     * @param os      the output stream where the PDF will be written
     */
    public ImagePDFCreator(HttpServletRequest request, OutputStream os) {
        this.imagePath = (String) request.getAttribute("imagePath");
        this.imageTitle = (String) request.getAttribute("imageTitle");
        this.os = os;
    }

    public ImagePDFCreator(String imagePath, String imageTitle, OutputStream os) {
        this.imagePath = imagePath;
        this.imageTitle = imageTitle;
        this.os = os;
    }

    /**
     * Prints the consultation request.
     *
     * @throws IOException       when an error with the output stream occurs
     * @throws DocumentException when an error in document construction occurs
     */
    public void printPdf() throws IOException, DocumentException {

        Image image;
        try {
            image = Image.getInstance(imagePath);
        } catch (Exception e) {
            logger.error("Unexpected error:", e);
            throw new DocumentException(e);
        }

        // Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);


        document.setPageSize(PageSize.LETTER);
        document.addCreator("OSCAR");
        document.open();

        int type = image.getOriginalType();
        if (type == Image.ORIGINAL_TIFF) {
            // The following is composed of code from com.itextpdf.tools.plugins.Tiff2Pdf modified to create the
            // PDF in memory instead of on disk
            RandomAccessFileOrArray ra = new RandomAccessFileOrArray(imagePath);
            int comps = TiffImage.getNumberOfPages(ra);
            boolean adjustSize = false;
            PdfContentByte cb = writer.getDirectContent();
            for (int c = 0; c < comps; ++c) {
                Image img = TiffImage.getTiffImage(ra, c + 1);
                if (img != null) {
                    if (adjustSize) {
                        document.setPageSize(new Rectangle(img.getScaledWidth(), img.getScaledHeight()));
                        document.newPage();
                        img.setAbsolutePosition(0, 0);
                    } else {
                        if (img.getScaledWidth() > 500 || img.getScaledHeight() > 700) {
                            img.scaleToFit(500, 700);
                        }
                        img.setAbsolutePosition(20, 20);
                        document.newPage();
                        document.add(new Paragraph(imageTitle + " - page " + (c + 1)));
                    }
                    cb.addImage(img);

                }
            }
            ra.close();
        } else {
            PdfContentByte cb = writer.getDirectContent();
            if (image.getScaledWidth() > 500 || image.getScaledHeight() > 700) {
                image.scaleToFit(500, 700);
            }
            image.setAbsolutePosition(20, 20);
            cb.addImage(image);
        }
        document.close();
    }
}
