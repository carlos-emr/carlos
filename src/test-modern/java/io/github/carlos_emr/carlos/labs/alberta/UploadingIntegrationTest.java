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
package io.github.carlos_emr.carlos.labs.alberta;

import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar.SendingUtils;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Integration tests for Alberta CLS lab uploading via {@link SendingUtils}.
 *
 * <p>Migrated from legacy JUnit 4 {@code UploadingTest}. The original test was
 * guarded by a static {@code ENABLED = false} flag, effectively disabling it.
 * This modern version preserves the same behavior using an assumption.</p>
 *
 * @see SendingUtils
 * @see TestLabs
 * @since 2012-01-01
 */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@DisplayName("Alberta Lab Uploading Integration Tests")
class UploadingIntegrationTest extends CarlosTestBase {

    private static final Logger log = LogManager.getLogger(UploadingIntegrationTest.class);

    /** Set to true to enable the test (requires running local server and valid keys). */
    private static final boolean ENABLED = false;

    /** Public OSCAR key (empty - requires system-specific configuration). */
    private static final String OSCAR_KEY = "";

    /** Public client key (empty - requires system-specific configuration). */
    private static final String CLIENT_KEY = "";

    @Test
    @DisplayName("should upload all CLS lab messages with valid RSA keys")
    void shouldUploadAllClsLabMessages_withValidRsaKeys() throws Exception {
        assumeThat(ENABLED)
            .as("Test is disabled by default - requires running local server and valid RSA keys")
            .isTrue();

        String url = "http://localhost:8080/oscar/lab/newLabUpload.do";
        String publicOscarKeyString = OSCAR_KEY;
        String publicServiceKeyString = CLIENT_KEY;

        PublicKey publicOscarKey = SendingUtils.getPublicOscarKey(publicOscarKeyString);
        PrivateKey publicServiceKey = SendingUtils.getPublicServiceKey(publicServiceKeyString);

        boolean isSuccess = true;
        for (int i = 0; i < TestLabs.ALL_LABS.length; i++) {
            String messageText = TestLabs.ALL_LABS[i];
            byte[] bytes = messageText.getBytes();
            int statusCode = SendingUtils.send(null, bytes, url, publicOscarKey, publicServiceKey, "CLS");
            log.info("Completed upload for " + TestLabs.LAB_NAMES[i] + " with status " + statusCode);
            isSuccess &= statusCode == 200;

            if (i == 0)
                break;
        }
        assertThat(isSuccess).isTrue();
    }
}
