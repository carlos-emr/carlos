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

package io.github.carlos_emr.carlos.oscarLab.ca.all.pageUtil;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import java.io.FileOutputStream;

// import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import org.openpdf.text.DocumentException;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import ca.uhn.hl7v2.HL7Exception;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.IHAPOIHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MEDITECHHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;

/**
 * Legacy JUnit 4 integration test for {@link LabPDFCreator}, verifying PDF generation
 * from HL7 lab messages produced by various Canadian laboratory information systems.
 *
 * <p>Each test method loads sample HL7 messages from a ZIP archive on the classpath,
 * parses them with the appropriate {@link MessageHandler} implementation, and renders
 * the results to PDF via {@link LabPDFCreator#printPdf()}. The output PDFs are written
 * to the classpath output directory for manual visual inspection.</p>
 *
 * <h3>Supported Lab Systems</h3>
 * <ul>
 *   <li><strong>MEDITECH</strong> -- HL7 format used by most rural Ontario health authorities</li>
 *   <li><strong>IHAPOI</strong> -- HL7 format used by rural BC health authorities
 *       (Interior Health Authority)</li>
 *   <li><strong>Excelleris (PATHL7)</strong> -- HL7 format used by Excelleris in both BC
 *       and Ontario</li>
 * </ul>
 *
 * <h3>Test Data</h3>
 * <p>Sample HL7 messages are stored as ZIP archives in the test classpath resources:
 * {@code MEDITECH_test_data.zip}, {@code IHAPOI_test_data.zip}, and
 * {@code excelleris_test_lab_data.zip}.</p>
 *
 * @see LabPDFCreator
 * @see MEDITECHHandler
 * @see IHAPOIHandler
 * @see PATHL7Handler
 */
public class LabPDFCreatorTest {

    /** Directory path where generated PDF output files are written for manual inspection. */
    private static String outputFilePath;

    /** Currently open ZIP archive containing HL7 test data; closed and replaced between test methods. */
    private static ZipFile zipFile;

    /**
     * Resolves the output directory to the root of the test classpath resources,
     * so generated PDF files appear alongside the test data archives.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        outputFilePath = Thread.currentThread().getContextClassLoader().getResource("").getFile();
    }

    /**
     * Tests PDF generation for MEDITECH HL7 lab messages.
     *
     * <p>MEDITECH is the HL7 format used by most rural Ontario health authorities.
     * Iterates through all entries in {@code MEDITECH_test_data.zip}, parses each
     * with {@link MEDITECHHandler}, and verifies that a non-empty PDF file is produced.</p>
     */
    @Test
    public void testPrintMeditech() {
        Enumeration<?> zipFile = openZipFile("MEDITECH_test_data.zip");

        while (zipFile.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) zipFile.nextElement();
            MEDITECHHandler handler = new MEDITECHHandler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertTrue(Files.exists(path));
        }
    }

    /**
     * Tests PDF generation for IHAPOI HL7 lab messages.
     *
     * <p>IHAPOI is the HL7 format used by rural BC health authorities, specifically
     * the Interior Health Authority (IHA) Point of Inquiry system. Iterates through
     * all entries in {@code IHAPOI_test_data.zip}, parses each with {@link IHAPOIHandler},
     * and verifies that a non-empty PDF file is produced.</p>
     */
    @Test
    public void testPrintIHAPOI() {
        Enumeration<?> zipFile = openZipFile("IHAPOI_test_data.zip");

        while (zipFile.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) zipFile.nextElement();
            IHAPOIHandler handler = new IHAPOIHandler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertTrue(Files.exists(path));
        }
    }

    /**
     * Tests PDF generation for Excelleris (PATHL7) HL7 lab messages.
     *
     * <p>Excelleris is a lab result delivery service used in both BC and Ontario.
     * Iterates through all entries in {@code excelleris_test_lab_data.zip}, parses each
     * with {@link PATHL7Handler}, and verifies that a non-empty PDF file is produced.</p>
     */
    @Test
    public void testPrintPathHl7() {

        Enumeration<?> zipFile = openZipFile("excelleris_test_lab_data.zip");

        while (zipFile.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) zipFile.nextElement();
            PATHL7Handler handler = new PATHL7Handler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertTrue(Files.exists(path));
        }
    }

    /**
     * Parses an HL7 message from a ZIP entry and renders it to a PDF file.
     *
     * <p>Extracts the HL7 body from the given {@link ZipEntry}, initializes the
     * provided {@link MessageHandler}, then uses {@link LabPDFCreator} to generate
     * a PDF. The output file is written to {@link #outputFilePath} with the entry
     * name (slashes replaced by underscores) plus a {@code .pdf} extension.</p>
     *
     * @param zipEntry the ZIP entry containing the HL7 message text
     * @param handler  the HL7 message handler appropriate for the lab system format
     * @return the absolute file path of the generated PDF, or an empty string if
     *         the HL7 body was empty or an error occurred
     */
    private static String createPDF(ZipEntry zipEntry, MessageHandler handler) {

        String hl7Body = getHL7Body(zipEntry);
        LabPDFCreator lpdfc = null;
        FileOutputStream output = null;
        String filePath = "";

        if (!hl7Body.isEmpty()) {

            lpdfc = new LabPDFCreator();
            lpdfc.setOs(new ByteArrayOutputStream());
            String filename = zipEntry.getName();

            try {
                MiscUtils.getLogger().info("Trying lab file " + filename);
                handler.init(hl7Body);
                lpdfc.setHandler(handler);
                lpdfc.printPdf();

                if (filename.contains("/")) {
                    filename = filename.replaceAll("/", "_");
                }
                filePath = outputFilePath + filename + ".pdf";
                output = new FileOutputStream(filePath);
                output.write(((ByteArrayOutputStream) lpdfc.getOs()).toByteArray());

                MiscUtils.getLogger().info("PDF file created at " + filePath);
            } catch (HL7Exception e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (DocumentException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        lpdfc.closeOs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return filePath;
    }

    /**
     * Reads the HL7 message body from a ZIP entry as a UTF-8 string.
     *
     * <p>If an I/O error occurs while reading, the {@link #zipFile} is closed and
     * set to null to prevent further reads from a potentially corrupt archive.</p>
     *
     * @param zipEntry the ZIP entry to read from the currently open {@link #zipFile}
     * @return the HL7 message text, or an empty string if reading fails
     */
    private static String getHL7Body(ZipEntry zipEntry) {

        StringWriter writer = new StringWriter();
        InputStream is = null;

        try {
            is = zipFile.getInputStream(zipEntry);
            IOUtils.copy(is, writer, "UTF-8");
        } catch (IOException e) {

            e.printStackTrace();

            if (zipFile != null) {
                try {
                    zipFile.close();
                    zipFile = null;
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }


        return writer.toString();
    }

    /**
     * Opens a ZIP archive from the test classpath and returns an enumeration of its entries.
     *
     * <p>If a previous ZIP file is still open, it is closed before opening the new one.
     * The opened archive is stored in the static {@link #zipFile} field for use by
     * {@link #getHL7Body(ZipEntry)}.</p>
     *
     * @param filename the name of the ZIP file on the test classpath (e.g.,
     *                 {@code "MEDITECH_test_data.zip"})
     * @return an enumeration of {@link ZipEntry} objects in the archive
     */
    private static Enumeration<? extends ZipEntry> openZipFile(String filename) {

        if (zipFile != null) {
            try {
                zipFile.close();
                zipFile = null;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        URL url = Thread.currentThread().getContextClassLoader().getResource(filename);

        try {
            zipFile = new ZipFile(url.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return zipFile.entries();
    }

}
