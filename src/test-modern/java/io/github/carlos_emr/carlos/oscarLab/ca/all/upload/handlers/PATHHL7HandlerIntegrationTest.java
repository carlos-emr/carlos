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
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for PATHL7 handler upload processing.
 *
 * <p>Migrated from legacy JUnit 4 parameterized {@code PATHHL7HandlerTest}.
 * The legacy test was marked with {@code @Ignore} pending issue resolution.
 * This modern version preserves the same disabled status via {@code @Disabled}.</p>
 *
 * @see PATHL7Handler
 * @see MessageUploader
 * @since 2012-01-01
 */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@DisplayName("PATHL7 Handler Integration Tests")
class PATHHL7HandlerIntegrationTest extends CarlosTestBase {

    private static final Logger log = LogManager.getLogger(PATHHL7HandlerIntegrationTest.class);

    @Test
    @Disabled("Skipping until underlying issue is resolved (ported from legacy @Ignore)")
    @DisplayName("should parse and route PATHL7 lab messages from zip archive")
    void shouldParseAndRoutePathl7LabMessages_fromZipArchive() {
        URL url = Thread.currentThread().getContextClassLoader().getResource("excelleris_test_lab_data.zip");
        List<String> hl7Bodies = new ArrayList<>();

        try (ZipFile zip = new ZipFile(url.getPath())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();

            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                if (zipEntry.getName().endsWith(".txt")) {
                    log.debug(zipEntry.getName());
                    try (InputStream is = zip.getInputStream(zipEntry)) {
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(is, writer, StandardCharsets.UTF_8);
                        hl7Bodies.add(writer.toString());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to read test zip file", e);
        }

        LoggedInInfo loggedInInfo = AuthUtils.initLoginContext();
        int testCount = 0;

        for (String hl7Body : hl7Bodies) {
            testCount++;
            log.info("#------------>>  Testing PATHHL7Handler Uploader for file: (" + testCount + ")");

            PATHL7Handler handler = new PATHL7Handler();
            final int count = testCount;
            final String body = hl7Body;

            assertThatNoException().isThrownBy(() -> {
                handler.init(body);
                MessageUploader.routeReport(loggedInInfo, "PATHHL7HandlerTest", "PATHL7", body, count, null);
            });
        }
    }
}
