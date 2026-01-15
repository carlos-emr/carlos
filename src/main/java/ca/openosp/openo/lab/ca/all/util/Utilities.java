//CHECKSTYLE:OFF
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


/*
 * Utilities.java
 *
 * Created on May 31, 2007, 2:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ca.openosp.openo.lab.ca.all.util;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;

import ca.openosp.OscarProperties;

public class Utilities {

    private static final Logger logger = MiscUtils.getLogger();

    private Utilities() {
        // utils shouldn't be instantiated
    }

    public static ArrayList<String> separateMessages(String fileName) throws Exception {

        ArrayList<String> messages = new ArrayList<String>();
        try (InputStream is = new FileInputStream(fileName);
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
    public static String saveFile(InputStream stream, String filename) {
        String retVal = null;

        try {
            OscarProperties props = OscarProperties.getInstance();
            String place = props.getProperty("DOCUMENT_DIR");

            // Validate filename and construct path using PathValidationUtils
            File safeDir = new File(place);
            File targetFile = PathValidationUtils.validatePath(filename, safeDir);

            // Construct retVal using the validated targetFile path
            retVal = targetFile.getParent() + File.separator + "LabUpload." + targetFile.getName().replaceAll(".enc", "") + "." + (new Date()).getTime();

            logger.debug("saveFile place=" + place + ", retVal=" + retVal);

            try (OutputStream os = Files.newOutputStream(Paths.get(retVal));
                BufferedInputStream bis = new BufferedInputStream(stream)) {

                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (FileNotFoundException fnfe) {
            logger.error("Unable to create or write to file: " + filename, fnfe);
        } catch (IOException ioe) {
            logger.error("Error processing file: " + filename, ioe);
        }
        return retVal;
    }

    public static String saveHRMFile(InputStream stream, String filename) {
        String retVal = null;
        String place = OscarProperties.getInstance().getProperty("OMD_hrm");

        try {
            if (!place.endsWith("/")) {
                place = new StringBuilder(place).insert(place.length(), "/").toString();
            }
            retVal = place + "KeyUpload." + filename + "." + (new Date()).getTime();

            //write the  file to the file specified
            OutputStream os = new FileOutputStream(retVal);

            int bytesRead = 0;
            while ((bytesRead = stream.read()) != -1) {
                os.write(bytesRead);
            }
            os.close();

            //close the stream
            stream.close();
        } catch (FileNotFoundException fnfe) {
            logger.error("Error", fnfe);
            return retVal;
        } catch (IOException ioe) {
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

            OscarProperties props = OscarProperties.getInstance();
            String place = props.getProperty("DOCUMENT_DIR");

            if (!place.endsWith("/")) {
                place = place + "/";
            }

            // Validate filename using PathValidationUtils
            File baseDir = new File(place);
            File targetFile = PathValidationUtils.validatePath(filename, baseDir);

            // Remove .enc
            filename = filename.replaceAll("\\.enc$", "");
            if (filename.toLowerCase().endsWith(".pdf")) {
                filename = filename.substring(0, filename.length() - 4);
            }

            retVal = new File(baseDir, "DocUpload." + filename + "." + System.currentTimeMillis() + ".pdf").toString();

            try (OutputStream os = new FileOutputStream(retVal)) {
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
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid filename: " + filename, iae);
            return null;
        }

        return retVal;
    }

    /*
     *  Return a string corresponding to the data in a given InputStream
     */
    public static String inputStreamAsString(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line = null;

        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }

        stream.close();
        br.close();
        return sb.toString();
    }
}
