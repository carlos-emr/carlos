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


/*
 * Utilities.java
 *
 * Created on May 31, 2007, 2:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab.ca.all.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;

public class Utilities {

    private static final Logger logger = MiscUtils.getLogger();

    private Utilities() {
        // utils shouldn't be instantiated
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static ArrayList<String> separateMessages(String fileName) throws Exception {

        File validatedFile = PathValidationUtils.validateExistingDocumentPath(fileName);

        ArrayList<String> messages = new ArrayList<String>();
        try (InputStream is = new FileInputStream(validatedFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            String line = null;
            boolean firstPIDflag = false; //true if the first PID segment has been processed false otherwise
            boolean firstMSHflag = false; //true if the first MSH segment has been processed false otherwise

            StringBuilder sb = new StringBuilder();
            String mshSeg = "";

            while ((line = br.readLine()) != null) {
                if (line.length() > 3) {
                    if (line.substring(0, 3).equals("MSH")) {
                        if (firstMSHflag) {
                            messages.add(sb.toString());
                            sb.delete(0, sb.length());
                        }
                        mshSeg = line;
                        firstMSHflag = true;
                        firstPIDflag = false;
                    } else if (line.substring(0, 3).equals("PID")) {
                        if (firstPIDflag) {
                            messages.add(sb.toString());
                            sb.delete(0, sb.length());
                            sb.append(mshSeg + "\r\n");
                        }
                        firstPIDflag = true;
                    }
                    sb.append(line + "\r\n");
                }
            }

            // add the last message
            messages.add(sb.toString());

        } catch (FileNotFoundException e) {
            MiscUtils.getLogger().error("File not found - ", e);
        } catch (IOException e) {
            MiscUtils.getLogger().error("An IOException occurred while working with file streams - ", e);
        }

        return (messages);
    }

    /**
     * @param stream
     * @param filename
     * @return String
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static String saveFile(InputStream stream, String filename) {
        try {
            File safeDir = PathValidationUtils.getRequiredDocumentDirectory();
            File targetFile = PathValidationUtils.validatePath(filename, safeDir);

            File outputFile = PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validateGeneratedFileName(
                            "LabUpload." + targetFile.getName().replaceFirst("\\.enc$", "") + "." + (new Date()).getTime()),
                    targetFile.getParentFile());

            // Only the configured directory is logged, never the generated name: it is derived from
            // the caller-supplied lab filename, which can carry patient-identifying text.
            if (logger.isDebugEnabled()) {
                logger.debug("saveFile place={}",
                        LogSafe.sanitize(safeDir.getPath(), 1024)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            }

            return writeUploadToNewFile(stream, outputFile) ? outputFile.getPath() : null;
        } catch (IOException ioe) {
            logger.error("Error processing file: {}", LogSafe.sanitize(filename), ioe); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }
    }

    /**
     * Streams the upload into {@code outputFile}, which must not already exist.
     *
     * @param stream the upload content; closed by this method
     * @param outputFile the validated destination, created exclusively by this call
     * @return {@code true} once written; {@code false} when another in-flight upload already owns the
     *         generated name, in which case nothing on disk is created or removed
     * @throws IOException if the write fails; the partial output is deleted first
     */
    private static boolean writeUploadToNewFile(InputStream stream, File outputFile) throws IOException {
        // CREATE_NEW, not the default CREATE/TRUNCATE_EXISTING: the generated name is only
        // millisecond-unique, so two concurrent uploads of the same filename can resolve to the same
        // path. Failing the second one is better than silently interleaving two labs into one file.
        // bis is declared FIRST on purpose: try-with-resources only closes resources it has already
        // constructed, so if the CREATE_NEW open below fails (name collision or any other open error)
        // a later-declared wrapper would never be built and the caller's stream would leak.
        try (BufferedInputStream bis = new BufferedInputStream(stream);
                OutputStream os = Files.newOutputStream(outputFile.toPath(),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {

            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            return true;
        } catch (FileAlreadyExistsException nameCollision) {
            // Another in-flight upload already created this name, so the file on disk is theirs. Fail
            // this upload without any cleanup rather than deleting their output. Neither the name nor
            // the exception is logged: both carry the lab filename.
            logger.error("Generated lab upload name is already in use; upload not written");
            return false;
        } catch (IOException writeFailure) {
            // Reachable only after CREATE_NEW succeeded above, so this call exclusively created
            // outputFile and removing it cannot discard another request's output.
            deletePartialOutput(outputFile);
            throw writeFailure;
        }
    }

    private static void deletePartialOutput(File outputFile) {
        if (outputFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputFile.toPath());
        } catch (IOException deleteException) {
            // Neither the path nor the throwable is logged: the generated name embeds the caller's lab
            // filename and the exception message repeats it. The exception type is enough to tell a
            // permissions failure from a missing file.
            logger.error("Error deleting partial lab upload output ({})", deleteException.getClass().getSimpleName());
        }
    }

    public static String saveHRMFile(InputStream stream, String filename) {
        String retVal = null;
        String place = CarlosProperties.getInstance().getProperty("OMD_hrm");

        try {
            File baseDir = PathValidationUtils.resolveConfiguredDirectory(place, "OMD_hrm");
            File outputFile = PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validateGeneratedFileName("KeyUpload." + filename + "." + (new Date()).getTime()),
                    baseDir);
            retVal = outputFile.getPath();

            try (OutputStream os = new FileOutputStream(outputFile)) {
                int bytesRead;
                while ((bytesRead = stream.read()) != -1) {
                    os.write(bytesRead);
                }
            }

            stream.close();
        } catch (FileNotFoundException fnfe) {
            logger.error("Error", fnfe);
            return retVal;
        } catch (IOException | SecurityException ioe) {
            logger.error("Error", ioe);
            return retVal;
        }
        return retVal;
    }

    public static String savePdfFile(InputStream stream, String filename) {
        String retVal = null;
        try {
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("Filename cannot be null or empty");
            }

            File baseDir = PathValidationUtils.getRequiredDocumentDirectory();
            File targetFile = PathValidationUtils.validatePath(filename, baseDir);

            // Derive the safe output name from the validated file (not the raw input)
            String safeName = targetFile.getName();
            // Remove .enc
            safeName = safeName.replaceAll("\\.enc$", "");
            if (safeName.toLowerCase().endsWith(".pdf")) {
                safeName = safeName.substring(0, safeName.length() - 4);
            }

            File outputFile = PathValidationUtils.validateGeneratedChildPath(
                    PathValidationUtils.validateGeneratedFileName("DocUpload." + safeName + "." + System.currentTimeMillis() + ".pdf"),
                    baseDir);
            retVal = outputFile.toString();

            try (OutputStream os = new FileOutputStream(outputFile)) {
                int bytesRead;
                while ((bytesRead = stream.read()) != -1) {
                    os.write(bytesRead);
                }
            }
            stream.close();

        } catch (FileNotFoundException fnfe) {
            logger.error("Error", fnfe);
            return retVal;
        } catch (IOException ioe) {
            logger.error("Error", ioe);
            return retVal;
        } catch (IllegalArgumentException | SecurityException iae) {
            logger.error("Invalid filename: {}", LogSafe.sanitize(filename), iae); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            return null;
        }

        return retVal;
    }
}
