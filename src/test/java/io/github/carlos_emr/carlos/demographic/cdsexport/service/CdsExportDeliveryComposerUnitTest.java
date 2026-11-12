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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportDeliveryComposerUnitTest {
    private static CdsExportRecipientFixture recipient;
    private final CdsExportDeliveryComposer composer = new CdsExportDeliveryComposer();

    @BeforeAll
    static void recipient() throws Exception {
        recipient = new CdsExportRecipientFixture();
    }

    @Test
    void shouldEncryptAndAuthenticateTheExactZip_toThePinnedRecipient() throws Exception {
        byte[] zip =
                "SYNTHETIC PRIVATE ZIP café 😀".repeat(100000).getBytes(StandardCharsets.UTF_8);
        var output = new ByteArrayOutputStream();
        var result =
                composer.compose(
                        new ByteArrayInputStream(zip),
                        output,
                        recipient.certificate,
                        recipient.fingerprint,
                        zip.length,
                        hash(zip),
                        CdsExportRecipientFixture.NOW,
                        zip.length + 100000,
                        () -> {});
        byte[] cipher = output.toByteArray();
        assertThat(result.zipSha256()).isEqualTo(hash(zip));
        assertThat(result.ciphertextSha256()).isEqualTo(hash(cipher));
        assertThat(result.ciphertextBytes()).isEqualTo(cipher.length);
        var parsed = new CMSAuthEnvelopedData(cipher);
        assertThat(parsed.getEncryptionAlgOID()).isEqualTo(CMSAlgorithm.AES256_GCM.getId());
        assertThat(parsed.getRecipientInfos().getRecipients()).hasSize(1);
        var info = parsed.getRecipientInfos().getRecipients().iterator().next();
        assertThat(
                        info.getContent(
                                new JceKeyTransAuthEnvelopedRecipient(recipient.keys.getPrivate())
                                        .setProvider(new BouncyCastleProvider())))
                .isEqualTo(zip);
        assertThat(new String(cipher, StandardCharsets.ISO_8859_1))
                .doesNotContain("SYNTHETIC PRIVATE ZIP");
    }

    @Test
    void shouldRejectChangedZipBytes_insteadOfReturningDeliveryEvidence() throws Exception {
        byte[] bytes = "fixture".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayInputStream(bytes),
                                        new ByteArrayOutputStream(),
                                        recipient.certificate,
                                        recipient.fingerprint,
                                        bytes.length,
                                        "a".repeat(64),
                                        CdsExportRecipientFixture.NOW,
                                        100000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
    }

    @Test
    void shouldRejectWrongPinOrExpiredCertificate_beforeWritingAnyCiphertext() throws Exception {
        var output = new ByteArrayOutputStream();
        byte[] bytes = {1};
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayInputStream(bytes),
                                        output,
                                        recipient.certificate,
                                        "a".repeat(64),
                                        1,
                                        hash(bytes),
                                        CdsExportRecipientFixture.NOW,
                                        100000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayInputStream(bytes),
                                        output,
                                        recipient.certificate,
                                        recipient.fingerprint,
                                        1,
                                        hash(bytes),
                                        CdsExportRecipientFixture.NOW.plusSeconds(86401),
                                        100000,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
        assertThat(output.size()).isZero();
    }

    @Test
    void shouldPropagateOutputFailure_withoutFallingBackToPlaintext() throws Exception {
        byte[] bytes = {1};
        OutputStream failed =
                new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("SYNTHETIC_DISK_FAILURE");
                    }
                };
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayInputStream(bytes),
                                        failed,
                                        recipient.certificate,
                                        recipient.fingerprint,
                                        1,
                                        hash(bytes),
                                        CdsExportRecipientFixture.NOW,
                                        100000,
                                        () -> {}))
                .hasMessage("SYNTHETIC_DISK_FAILURE");
    }

    @Test
    void shouldRefuseCiphertextResourceLimits_beforePublishingAnArtifact() throws Exception {
        byte[] bytes = {1};
        assertThatThrownBy(
                        () ->
                                composer.compose(
                                        new ByteArrayInputStream(bytes),
                                        new ByteArrayOutputStream(),
                                        recipient.certificate,
                                        recipient.fingerprint,
                                        1,
                                        hash(bytes),
                                        CdsExportRecipientFixture.NOW,
                                        10,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_DELIVERY_BYTE_LIMIT");
    }

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
