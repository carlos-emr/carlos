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
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

//import static org.junit.Assert.*;


import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.junit.Ignore;
import org.junit.runners.Parameterized;
import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;

@Ignore //Skipping until issue is resolved
public class PATHHL7HandlerTest {

    private static Logger logger = io.github.carlos_emr.carlos.utility.MiscUtils.getLogger();
    private static String hl7Body;
    private static ZipFile zipFile;
    private static int TEST_COUNT = 0;

    @Parameterized.Parameters
    public static Collection<String[]> hl7BodyArray() {

        logger.info("Creating PATHHL7HandlerTest test parameters");

        URL url = Thread.currentThread().getContextClassLoader().getResource("excelleris_test_lab_data.zip");

        try {
            zipFile = new ZipFile(url.getPath());
        } catch (IOException e) {
            logger.error("Test Failed ", e);
        }

        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        StringWriter writer = null;
        InputStream is = null;
        List<String[]> hl7BodyArray = new ArrayList<String[]>();
        String hl7Body = "";

        while (enumeration.hasMoreElements()) {

            ZipEntry zipEntry = enumeration.nextElement();

            if (zipEntry.getName().endsWith(".txt")) {

                logger.debug(zipEntry.getName());

                writer = new StringWriter();

                try {
                    is = zipFile.getInputStream(zipEntry);
                    IOUtils.copy(is, writer, StandardCharsets.UTF_8);
                } catch (IOException e) {

                    if (zipFile != null) {
                        try {
                            zipFile.close();
                            zipFile = null;
                        } catch (IOException e1) {
                            logger.error("Test Failed ", e);
                        }
                    }
                    logger.error("Test Failed ", e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                            is = null;
                        } catch (IOException e) {
                            logger.error("Test Failed ", e);
                        }
                    }


                }

                hl7Body = writer.toString();
                hl7BodyArray.add(new String[]{hl7Body});
            }
        }
        return hl7BodyArray;
    }

    public PATHHL7HandlerTest(String hl7Body) {
        PATHHL7HandlerTest.hl7Body = hl7Body;
    }

    @Ignore //Skipping until issue is resolved
    public void testParse() {
        TEST_COUNT += 1;

        logger.info("#------------>>  Testing PATHHL7Handler Uploader for file: (" + TEST_COUNT + ")");

        LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
        PATHL7Handler pATHHL7Handler = new PATHL7Handler();

        try {
            pATHHL7Handler.init(hl7Body);
            MessageUploader.routeReport(loggedInInfo, "PATHHL7HandlerTest", "PATHL7", hl7Body, TEST_COUNT, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
