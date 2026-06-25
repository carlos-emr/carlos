/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigitalSignatureUtils#storeDigitalSignatureFromTempFileToDB}.
 *
 * <p>Pins the behaviour of the {@code readAllBytes()} read: the persisted signature image must be a
 * byte-for-byte copy of the collected file, for both a small payload (the previous fixed 256&nbsp;KB
 * array persisted trailing padding) and a payload larger than that array (which would have been
 * truncated). The image is PHI, so an exact copy matters clinically and legally.</p>
 *
 * <p>The temp file is written to the real {@code java.io.tmpdir} under a unique request id (and
 * deleted afterward) because {@link DigitalSignatureUtils#getTempFilePath} derives the path from
 * that system property; the persisting DAO is mocked via {@link CarlosUnitTestBase}.</p>
 *
 * @since 2026-06-01
 */
@DisplayName("DigitalSignatureUtils.storeDigitalSignatureFromTempFileToDB")
class DigitalSignatureUtilsUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_ID = 1373;

    private LoggedInInfo loggedInInfo;
    private final List<Path> writtenFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Facility facility = mock(Facility.class);
        when(facility.isEnableDigitalSignatures()).thenReturn(true);
        when(facility.getId()).thenReturn(1);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // storeDigitalSignatureFromTempFileToDB persists via SpringUtils.getBean(DigitalSignatureDao.class).
        createAndRegisterMock(DigitalSignatureDao.class);
    }

    @AfterEach
    void cleanUpFiles() throws IOException {
        for (Path p : writtenFiles) {
            Files.deleteIfExists(p);
        }
    }

    @Test
    @DisplayName("should persist the exact signature bytes for a small payload")
    void shouldPersistExactBytes_forSmallPayload() throws IOException {
        byte[] expected = "small-signature-bytes".getBytes(StandardCharsets.UTF_8);

        DigitalSignature result = storeSignature("small", expected);

        assertThat(result).isNotNull();
        assertThat(result.getSignatureImage()).isEqualTo(expected);
    }

    @Test
    @DisplayName("should persist the exact signature bytes for a payload larger than the old fixed buffer")
    void shouldPersistExactBytes_forLargePayload() throws IOException {
        // Larger than the removed fixed 256 KB array, so a truncating read would lose the tail.
        byte[] expected = new byte[300_000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i % 256);
        }

        DigitalSignature result = storeSignature("large", expected);

        assertThat(result).isNotNull();
        assertThat(result.getSignatureImage()).hasSameSizeAs(expected);
        assertThat(result.getSignatureImage()).isEqualTo(expected);
    }

    /**
     * Writes {@code bytes} to the temp path the production code will read, then runs the store method.
     * Uses a unique request id so concurrent tests do not collide on the shared temp directory.
     */
    private DigitalSignature storeSignature(String idPrefix, byte[] bytes) throws IOException {
        String signatureRequestId = "junit" + idPrefix + System.nanoTime();
        Path file = Paths.get(DigitalSignatureUtils.getTempFilePath(signatureRequestId));
        writtenFiles.add(file);
        Files.write(file, bytes);
        return DigitalSignatureUtils.storeDigitalSignatureFromTempFileToDB(loggedInInfo, signatureRequestId, DEMOGRAPHIC_ID);
    }
}
