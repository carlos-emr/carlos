/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;
import io.github.carlos_emr.carlos.util.UtilMisc;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class JdkEncodingCompatibilityUnitTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 56, 57, 58, 256, 1_048_577})
    void shouldMatchPaddedUnchunkedCodec_forBinaryEncoders(int size) throws Exception {
        byte[] bytes = new byte[size];
        new Random(5483).nextBytes(bytes);
        byte[] expected = Base64.encodeBase64(bytes);
        assertThat(java.util.Base64.getEncoder().encode(bytes)).isEqualTo(expected);
        assertThat(java.util.Base64.getEncoder().encodeToString(bytes)).isEqualTo(Base64.encodeBase64String(bytes));
        ClientImage image = new ClientImage();
        image.setImage_data(bytes);
        var blob = image.getImage_contents();
        try (var contents = blob.getBinaryStream()) {
            assertThat(contents.readAllBytes()).isEqualTo(expected);
        }
        var xml = XmlUtils.newDocument("root");
        XmlUtils.appendChildToRoot(xml, "attachment", bytes);
        assertThat(xml.getDocumentElement().getTextContent()).isEqualTo(new String(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab", "abc", "é漢字🩺", "synthetic\r\nmessage"})
    void shouldPreserveTextAndEmailStorage_forEncodingCallers(String text) {
        String expectedDefault = new String(Base64.encodeBase64(text.getBytes()));
        assertThat(UtilMisc.encode64(text)).isEqualTo(expectedDefault);
        assertThat(MsgCommxml.encode64(text)).isEqualTo(expectedDefault);
        EmailLog email = new EmailLog(null, "sender", null, "synthetic", text, null);
        assertThat(email.getBody()).isEqualTo(text);
        email.setBody(text);
        email.setEncryptedMessage(text);
        email.setInternalComment(text);
        assertThat(email.getBody()).isEqualTo(text);
        assertThat(email.getEncryptedMessage()).isEqualTo(text);
        assertThat(email.getInternalComment()).isEqualTo(text);
        assertThat(java.util.Base64.getEncoder().encode(text.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(Base64.encodeBase64(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRetainNullContracts_forEncodingCallers() {
        assertThat(new ClientImage().getImage_contents()).isNull();
        assertThatThrownBy(() -> UtilMisc.encode64(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MsgCommxml.encode64(null)).isInstanceOf(NullPointerException.class);
        EmailLog email = new EmailLog();
        email.setBody(null);
        assertThat(email.getBody()).isNull();
    }

    @Test
    void shouldKeepLenientDecoding_forStoredLegacyData() throws Exception {
        byte[] expected = {(byte) 251, (byte) 255};
        ClientImage image = new ClientImage();
        image.setImage_contents(new javax.sql.rowset.serial.SerialBlob(" -_8 !\r\n".getBytes(StandardCharsets.US_ASCII)));
        assertThat(image.getImage_data()).isEqualTo(expected);
        var decoder = java.util.Base64.getDecoder();
        assertThatThrownBy(() -> decoder.decode(" -_8 !\r\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
