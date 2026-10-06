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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@DisplayName("LabUpload2Action message decryption")
@Tag("unit")
class LabUpload2ActionDecryptUnitTest extends CarlosUnitTestBase {

    private static final String MESSAGE = "MSH|^~\\&|FAKE-LAB|FAKE-SITE|||20260101000000||ORU^R01|1|P|2.3";
    /** A long unbroken Base64-alphabet run: a stored value written out in full. */
    private static final Pattern ENCODED_RUN = Pattern.compile("[A-Za-z0-9+/=]{64,}");
    private static final int MAX_CAUSE_DEPTH = 10;

    private OscarKeyDao oscarKeyDao;

    @BeforeEach
    void setUp() {
        oscarKeyDao = mock(OscarKeyDao.class);
        registerMock(OscarKeyDao.class, oscarKeyDao);
    }

    @Test
    @DisplayName("should decrypt an uploaded message with the stored server key and keep the log brief")
    void shouldDecryptUploadedMessage_withStoredServerKey() throws Exception {
        KeyPair server = rsaKeyPair();
        OscarKey stored = storedKey(encode(server.getPublic().getEncoded()), encode(server.getPrivate().getEncoded()));
        when(oscarKeyDao.find("oscar")).thenReturn(stored);

        SecretKey messageKey = KeyGenerator.getInstance("AES").generateKey();
        // codeql[java/rsa-without-oaep] Test plays the sending lab: the legacy lab-upload wire format requires RSA/ECB/PKCS1Padding (LabUpload2Action unwraps with it)
        Cipher wrap = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        wrap.init(Cipher.ENCRYPT_MODE, server.getPublic());
        String wrappedKey = encode(wrap.doFinal(messageKey.getEncoded()));
        Cipher body = Cipher.getInstance("AES"); // legacy wire format under test
        body.init(Cipher.ENCRYPT_MODE, messageKey);
        byte[] encrypted = body.doFinal(MESSAGE.getBytes(StandardCharsets.UTF_8));

        try (LogCapture capture = LogCapture.forLogger(LabUpload2Action.class)) {
            String decrypted = readAll(LabUpload2Action.decryptMessage(new ByteArrayInputStream(encrypted), wrappedKey, null));

            assertThat(decrypted).isEqualTo(MESSAGE);
            assertLogBrief(loggedText(capture), stored);
        }
    }

    @Test
    @DisplayName("should return no stream for an unusable stored server key and keep the log brief")
    void shouldReturnNull_whenStoredServerKeyIsUnusable() {
        String unusable = encode("FAKE-stored-value-that-is-not-a-pkcs8-key-".repeat(4).getBytes(StandardCharsets.UTF_8));
        OscarKey stored = storedKey(unusable, unusable);
        when(oscarKeyDao.find("oscar")).thenReturn(stored);

        try (LogCapture capture = LogCapture.forLogger(LabUpload2Action.class)) {
            InputStream decrypted = LabUpload2Action.decryptMessage(new ByteArrayInputStream(new byte[16]), encode(new byte[256]), null);

            assertThat(decrypted).isNull();
            assertThat(capture.events()).as("the failure is still reported").isNotEmpty();
            assertLogBrief(loggedText(capture), stored);
        }
    }

    private static void assertLogBrief(String logged, OscarKey stored) {
        assertThat(logged)
                .as("upload log")
                .doesNotContain(stored.getPrivateKey())
                .doesNotContain(stored.getPublicKey())
                .doesNotContain("privateKey=")
                .doesNotContain("publicKey=")
                .doesNotContainPattern(ENCODED_RUN);
    }

    /** Every formatted message plus every attached exception (and its causes), as one block of text. */
    private static String loggedText(LogCapture capture) {
        StringBuilder text = new StringBuilder();
        for (LogEvent event : capture.events()) {
            text.append(event.getMessage().getFormattedMessage()).append('\n');
            Throwable thrown = event.getThrown();
            for (int depth = 0; thrown != null && depth < MAX_CAUSE_DEPTH; depth++) {
                text.append(thrown).append('\n');
                thrown = thrown.getCause();
            }
        }
        return text.toString();
    }

    private static OscarKey storedKey(String publicKey, String privateKey) {
        OscarKey key = new OscarKey();
        key.setName("oscar");
        key.setPublicKey(publicKey);
        key.setPrivateKey(privateKey);
        return key;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String readAll(InputStream in) throws IOException {
        assertThat(in).as("decryptMessage returned a stream").isNotNull();
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
