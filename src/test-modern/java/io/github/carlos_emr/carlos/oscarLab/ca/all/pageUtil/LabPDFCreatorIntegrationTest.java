/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.oscarLab.ca.all.pageUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
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

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openpdf.text.DocumentException;

import ca.uhn.hl7v2.HL7Exception;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.IHAPOIHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MEDITECHHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link LabPDFCreator}, verifying PDF generation from
 * HL7 lab messages produced by various Canadian laboratory information systems.
 *
 * <p>Each test method loads sample HL7 messages from a ZIP archive on the classpath,
 * parses them with the appropriate {@link MessageHandler} implementation, and renders
 * the results to PDF via {@link LabPDFCreator#printPdf()}.</p>
 *
 * <h3>Supported Lab Systems</h3>
 * <ul>
 *   <li><strong>MEDITECH</strong> -- HL7 format used by most rural Ontario health authorities</li>
 *   <li><strong>IHAPOI</strong> -- HL7 format used by rural BC health authorities</li>
 *   <li><strong>Excelleris (PATHL7)</strong> -- HL7 format used by Excelleris in BC and Ontario</li>
 * </ul>
 *
 * <p>Migrated from legacy JUnit 4 {@code LabPDFCreatorTest}.</p>
 *
 * @see LabPDFCreator
 * @see MEDITECHHandler
 * @see IHAPOIHandler
 * @see PATHL7Handler
 * @since 2012-01-01
 */
@Tag("integration")
@Tag("lab")
@Tag("pdf")
@DisplayName("LabPDFCreator Integration Tests")
class LabPDFCreatorIntegrationTest {

    private static final Logger logger = LogManager.getLogger(LabPDFCreatorIntegrationTest.class);

    /** Directory path where generated PDF output files are written for manual inspection. */
    private static String outputFilePath;

    /** Currently open ZIP archive containing HL7 test data. */
    private static ZipFile zipFile;

    @BeforeAll
    static void setUpBeforeAll() {
        outputFilePath = Thread.currentThread().getContextClassLoader().getResource("").getFile();
    }

    @Test
    @DisplayName("should generate PDF from MEDITECH HL7 lab messages")
    void shouldGeneratePdf_fromMeditechHl7LabMessages() {
        Enumeration<?> entries = openZipFile("MEDITECH_test_data.zip");

        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            MEDITECHHandler handler = new MEDITECHHandler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertThat(Files.exists(path)).isTrue();
        }
    }

    @Test
    @DisplayName("should generate PDF from IHAPOI HL7 lab messages")
    void shouldGeneratePdf_fromIhapoiHl7LabMessages() {
        Enumeration<?> entries = openZipFile("IHAPOI_test_data.zip");

        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            IHAPOIHandler handler = new IHAPOIHandler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertThat(Files.exists(path)).isTrue();
        }
    }

    @Test
    @DisplayName("should generate PDF from Excelleris PATHL7 HL7 lab messages")
    void shouldGeneratePdf_fromExcellerisPathl7LabMessages() {
        Enumeration<?> entries = openZipFile("excelleris_test_lab_data.zip");

        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            PATHL7Handler handler = new PATHL7Handler();
            Path path = Paths.get(createPDF(zipEntry, handler));
            assertThat(Files.exists(path)).isTrue();
        }
    }

    /**
     * Parses an HL7 message from a ZIP entry and renders it to a PDF file.
     *
     * @param zipEntry the ZIP entry containing the HL7 message text
     * @param handler  the HL7 message handler appropriate for the lab system format
     * @return the absolute file path of the generated PDF, or an empty string on failure
     */
    private static String createPDF(ZipEntry zipEntry, MessageHandler handler) {
        String hl7Body = getHL7Body(zipEntry);
        String filePath = "";

        if (!hl7Body.isEmpty()) {
            LabPDFCreator lpdfc = new LabPDFCreator();
            lpdfc.setOs(new ByteArrayOutputStream());
            String filename = zipEntry.getName();

            try {
                logger.info("Trying lab file " + filename);
                handler.init(hl7Body);
                lpdfc.setHandler(handler);
                lpdfc.printPdf();

                if (filename.contains("/")) {
                    filename = filename.replaceAll("/", "_");
                }
                filePath = outputFilePath + filename + ".pdf";
                try (FileOutputStream output = new FileOutputStream(filePath)) {
                    output.write(((ByteArrayOutputStream) lpdfc.getOs()).toByteArray());
                }

                logger.info("PDF file created at " + filePath);
            } catch (HL7Exception | IOException | DocumentException e) {
                throw new AssertionError("PDF creation failed for " + filename, e);
            } finally {
                try {
                    lpdfc.closeOs();
                } catch (IOException e) {
                    logger.warn("Failed to close LabPDFCreator output stream", e);
                }
            }
        }

        return filePath;
    }

    /**
     * Reads the HL7 message body from a ZIP entry as a UTF-8 string.
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
            throw new AssertionError("Failed to read HL7 body from zip entry", e);
        }

        return writer.toString();
    }

    /**
     * Opens a ZIP archive from the test classpath and returns an enumeration of its entries.
     *
     * @param filename the name of the ZIP file on the test classpath
     * @return an enumeration of {@link ZipEntry} objects in the archive
     */
    private static Enumeration<? extends ZipEntry> openZipFile(String filename) {
        if (zipFile != null) {
            try {
                zipFile.close();
                zipFile = null;
            } catch (IOException e1) {
                logger.error("Failed to close previous zip file", e1);
            }
        }

        URL url = Thread.currentThread().getContextClassLoader().getResource(filename);

        try {
            zipFile = new ZipFile(url.getPath());
        } catch (IOException e) {
            throw new AssertionError("Failed to open test zip file: " + filename, e);
        }

        return zipFile.entries();
    }
}
