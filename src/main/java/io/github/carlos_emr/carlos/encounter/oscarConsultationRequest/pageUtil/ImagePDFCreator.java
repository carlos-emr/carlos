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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Iterator;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Converts image files to PDF documents for inclusion in consultation request attachments.
 *
 * <p>Supports standard image formats (JPEG, PNG, GIF, BMP) via OpenPDF's {@link Image} class,
 * and multi-page TIFF files via the TwelveMonkeys ImageIO TIFF plugin. The built-in TiffImage
 * codec was removed in OpenPDF 3.x, so TIFF handling uses {@link javax.imageio.ImageIO} with
 * the TwelveMonkeys plugin to read individual pages and convert them to PDF pages.</p>
 *
 * <p>Extends {@link PdfPageEventHelper} to participate in OpenPDF page lifecycle events.
 * File path security is enforced via {@link PathValidationUtils} to prevent path traversal
 * attacks against the configured document directory.</p>
 *
 * @see ConsultationPDFCreator
 * @see PathValidationUtils
 * @since 2012-04-09
 */
public class ImagePDFCreator extends PdfPageEventHelper {

    private static Logger logger = MiscUtils.getLogger();
    private OutputStream os;
    private String imagePath;
    private String imageTitle;
    private Document document;

    /**
     * Constructs an ImagePDFCreator from an HTTP request containing image path attributes.
     *
     * <p>Expects the following request attributes:</p>
     * <ul>
     *   <li>{@code imagePath} - absolute path to the image file</li>
     *   <li>{@code imageTitle} - descriptive title for the image in the PDF</li>
     * </ul>
     *
     * @param request HttpServletRequest containing {@code imagePath} and {@code imageTitle} attributes
     * @param os      OutputStream where the generated PDF will be written
     */
    public ImagePDFCreator(HttpServletRequest request, OutputStream os) {
        this.imagePath = (String) request.getAttribute("imagePath");
        this.imageTitle = (String) request.getAttribute("imageTitle");
        this.os = os;
    }

    /**
     * Constructs an ImagePDFCreator with explicit image path and title.
     *
     * @param imagePath  String absolute filesystem path to the image file
     * @param imageTitle String descriptive title displayed in the PDF for each page
     * @param os         OutputStream where the generated PDF will be written
     */
    public ImagePDFCreator(String imagePath, String imageTitle, OutputStream os) {
        this.imagePath = imagePath;
        this.imageTitle = imageTitle;
        this.os = os;
    }

    /**
     * Generates a PDF document from the configured image file.
     *
     * <p>For TIFF files (detected by {@code .tif} or {@code .tiff} extension), each page in the
     * multi-page TIFF is rendered as a separate PDF page using the TwelveMonkeys ImageIO TIFF
     * plugin. For all other image formats, OpenPDF's {@link Image#getInstance(String)} handles
     * the conversion directly.</p>
     *
     * <p>Images exceeding 500x700 points are scaled to fit within those bounds.
     * The image path is validated against the configured {@code DOCUMENT_DIR} property
     * using {@link PathValidationUtils} before any file access occurs.</p>
     *
     * @throws IOException       when an I/O error occurs reading the image or writing the PDF
     * @throws DocumentException when OpenPDF encounters an error constructing the PDF document
     */
    public void printPdf() throws IOException, DocumentException {

        // Validate imagePath against the configured document directory to prevent path traversal
        if (imagePath == null || imagePath.isEmpty()) {
            throw new DocumentException("Image path is null or empty");
        }
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (documentDir == null || documentDir.isEmpty()) {
            logger.error("DOCUMENT_DIR property is not configured");
            throw new DocumentException("Document directory is not configured");
        }
        File imageFile;
        try {
            imageFile = PathValidationUtils.validateExistingPath(new File(imagePath), new File(documentDir));
        } catch (SecurityException e) {
            logger.error("Image path validation failed - access outside document directory blocked");
            throw new DocumentException("Invalid image path");
        }

        // Create the document we are going to write to
        document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);

        document.setPageSize(PageSize.LETTER);
        document.addCreator("CARLOS EMR");
        document.open();

        try {
            // Detect TIFF by extension before calling Image.getInstance(), which throws
            // for TIFF files in OpenPDF 3.x (built-in TiffImage codec was removed)
            String lowerPath = imageFile.getName().toLowerCase(java.util.Locale.ROOT);
            boolean isTiff = lowerPath.endsWith(".tif") || lowerPath.endsWith(".tiff");

            if (isTiff) {
                // Multi-page TIFF handling via ImageIO + TwelveMonkeys TIFF plugin
                // (OpenPDF 3.0 removed built-in TiffImage codec)
                try (ImageInputStream iis = ImageIO.createImageInputStream(imageFile)) {
                    if (iis == null) {
                        throw new DocumentException("Unable to read image input stream");
                    }
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                    if (!readers.hasNext()) {
                        throw new DocumentException("No TIFF ImageReader found for supplied image");
                    }
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis);
                        int comps = reader.getNumImages(true);
                        if (comps <= 0) {
                            throw new DocumentException("TIFF image contains no readable pages");
                        }
                        PdfContentByte cb = writer.getDirectContent();
                        for (int c = 0; c < comps; c++) {
                            BufferedImage bufferedImage = reader.read(c);
                            if (bufferedImage == null) {
                                logger.warn("Skipping unreadable TIFF page {}", c + 1);
                                continue;
                            }
                            Image img = Image.getInstance(bufferedImage, null);
                            if (img.getScaledWidth() > 500 || img.getScaledHeight() > 700) {
                                img.scaleToFit(500, 700);
                            }
                            img.setAbsolutePosition(20, 20);
                            document.newPage();
                            document.add(new Paragraph(imageTitle + " - page " + (c + 1)));
                            cb.addImage(img);
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            } else {
                Image image;
                try {
                    image = Image.getInstance(imageFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Unexpected error loading image");
                    throw new DocumentException(e);
                }
                PdfContentByte cb = writer.getDirectContent();
                if (image.getScaledWidth() > 500 || image.getScaledHeight() > 700) {
                    image.scaleToFit(500, 700);
                }
                image.setAbsolutePosition(20, 20);
                cb.addImage(image);
            }
        } finally {
            try {
                if (document.isOpen()) {
                    document.close();
                }
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }
}
