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


package io.github.carlos_emr.carlos.billings.ca.bc.Teleplan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * @author jay
 */
public class TeleplanResponse {
    static Logger log = MiscUtils.getLogger();
    private static final String DOCUMENT_DIR_PROPERTY = "DOCUMENT_DIR";
    private String transactionNo = null;
    private String result = null;
    private String filename = null;
    private String realFilename = null;
    private String msgs = null;
    private int lineCount = 0;

    /**
     * Creates a new instance of TeleplanResponse
     */
    public TeleplanResponse() {
    }


    // FindSecBugs PREDICTABLE_RANDOM: Math.random only adds a local Teleplan temp filename suffix.
    // Do not use this suppression for secrets, tokens, authorization, or request-controlled random values.
    @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "Math.random only creates a local Teleplan temporary filename suffix, not a secret, token, or authorization decision")
    void processResponseStream(InputStream in) {
        File tempFile = null;
        try {
            File directory = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty(DOCUMENT_DIR_PROPERTY, "./"), DOCUMENT_DIR_PROPERTY);
            double randNum = Math.random();
            tempFile = PathValidationUtils.validateGeneratedChildPath(PathValidationUtils.validateGeneratedFileName("teleplan.msp" + randNum), directory);

            String str = "";
            String lastLine = null;
            try (BufferedReader bin = new BufferedReader(new InputStreamReader(in));
                    BufferedWriter out = new BufferedWriter(new FileWriter(tempFile))) {
                while ((str = bin.readLine()) != null) {
                    //write str to temp file
                    lineCount++;
                    out.write(str + "\n");
                    // Do not log the raw Teleplan response line: it can contain claim/billing
                    // (PHI-correlating) content. Log only non-sensitive length metadata.
                    log.debug("Read Teleplan response line ({} chars)", str.length());
                    lastLine = str;
                }
            }
            // Guard the empty-response case: with no lines read, lastLine stays null and
            // processLastLine(null) would NPE (and lineCount would go negative).
            if (lastLine != null) {
                lineCount--;
                processLastLine(lastLine);
            }
            //If it has a filename same to

            if (this.getFilename() != null && !this.getFilename().trim().equals("")) {
                File file = PathValidationUtils.validateExistingPath(tempFile, directory);
                realFilename = "teleplan" + this.getFilename() + randNum;

                // Use PathValidationUtils to validate destination path
                File allowedDir = directory;
                File file2;
                try {
                    file2 = PathValidationUtils.validatePath(realFilename, allowedDir);
                } catch (SecurityException e) {
                    throw new SecurityException("File access not allowed outside designated directory");
                }

                boolean success = file.renameTo(file2);
                if (!success) {
                    log.error("File was not successfully renamed");
                }
            }


        } catch (IOException | SecurityException e) {
            // SecurityException covers PathValidationUtils rejecting a misconfigured DOCUMENT_DIR or a
            // generated destination; clean up the partial temp file like the IOException path.
            MiscUtils.getLogger().error("Error", e);
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                log.warn("Could not delete partial Teleplan response file");
            }
        }
    }


    //#TID=001;Result=SUCCESS;Filename=TPBULET-I.txt;Msgs=;
    //	String str = "#TID=001;Result=SUCCESS;Filename=TPBULET-I.txt;Msgs
    void processLastLine(String str) {
        int idx = str.indexOf("Msgs=");
        msgs = str.substring(idx + 5, str.lastIndexOf(';'));
        str = str.substring(0, idx);
        idx = str.indexOf("Filename=");
        filename = str.substring(idx + 9, str.lastIndexOf(';'));
        str = str.substring(0, idx);
        idx = str.indexOf("Result=");
        result = str.substring(idx + 7, str.lastIndexOf(';'));
        str = str.substring(0, idx);
        idx = str.indexOf("#TID=");
        transactionNo = str.substring(idx + 5, str.lastIndexOf(';'));
    }

    public String toString() {
        return "#TID=" + getTransactionNo() + ";Result=" + getResult() + ";Filename=" + getFilename() + ";Msgs=" + getMsgs() + "; NUM LINES " + lineCount + " REALFILNAME =" + realFilename;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public String getResult() {
        return result;
    }

    public boolean isFailure() {
        return result.equals("FAILURE");
    }

    public boolean isSuccess() {
        return result.equals("SUCCESS");
    }

    public String getFilename() {
        return filename;
    }

    public String getRealFilename() {
        return realFilename;
    }

    public String getMsgs() {
        return msgs;
    }

    public File getFile() {
        File directory = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty(DOCUMENT_DIR_PROPERTY, "./"), DOCUMENT_DIR_PROPERTY);
        return PathValidationUtils.validatePath(realFilename, directory);
    }

}
