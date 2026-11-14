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
                                        () -> {}))
                .hasMessage("SYNTHETIC_DISK_FAILURE");
    }

    /** Reads a POSIX ustar archive into its members, in order. */
    private static java.util.LinkedHashMap<String, byte[]> tar(byte[] archive) {
        var members = new java.util.LinkedHashMap<String, byte[]>();
        int at = 0;
        while (true) {
            byte[] header = java.util.Arrays.copyOfRange(archive, at, at + 512);
            if (header[0] == 0) break;
            String name = new String(header, 0, 100, StandardCharsets.US_ASCII).replace("\0", "");
            int size =
                    Integer.parseInt(
                            new String(header, 124, 11, StandardCharsets.US_ASCII), 8);
            // The checksum counts its own field as eight spaces.
            int stored = Integer.parseInt(new String(header, 148, 6, StandardCharsets.US_ASCII), 8);
            java.util.Arrays.fill(header, 148, 156, (byte) ' ');
            int sum = 0;
            for (byte value : header) sum += value & 255;
            assertThat(sum).isEqualTo(stored);
            assertThat(new String(header, 257, 5, StandardCharsets.US_ASCII)).isEqualTo("ustar");
            members.put(name, java.util.Arrays.copyOfRange(archive, at + 512, at + 512 + size));
            at += 512 + (size + 511) / 512 * 512;
        }
        // Two zero blocks end the archive, and nothing follows them.
        assertThat(archive.length).isEqualTo(at + 1024);
        return members;
    }

    private static byte[] decrypt(byte[] cipher) throws Exception {
        var parsed = new CMSAuthEnvelopedData(cipher);
        assertThat(parsed.getEncryptionAlgOID()).isEqualTo(CMSAlgorithm.AES256_GCM.getId());
        var info = parsed.getRecipientInfos().getRecipients().iterator().next();
        return info.getContent(
                new JceKeyTransAuthEnvelopedRecipient(recipient.keys.getPrivate())
                        .setProvider(new BouncyCastleProvider()));
    }

    @Test
    void shouldDeliverAZipLargerThanOneCipherMessage_asPartsTheRecipientCanRejoin()
            throws Exception {
        // The cipher's single-message size and the part size are injected small: 10,000 bytes of
        // ZIP against a 4,096-byte "one message" ceiling and 1,000-byte parts.
        byte[] zip = new byte[10_000];
        new java.util.Random(7).nextBytes(zip);
        var output = new ByteArrayOutputStream();
        var result =
                new CdsExportDeliveryComposer(4096, 1000)
                        .compose(
                                new ByteArrayInputStream(zip),
                                output,
                                recipient.certificate,
                                recipient.fingerprint,
                                zip.length,
                                hash(zip),
                                CdsExportRecipientFixture.NOW,
                                () -> {});
        byte[] archive = output.toByteArray();
        assertThat(result.encryptionProfile()).isEqualTo(CdsExportDeliveryComposer.PARTS_PROFILE);
        assertThat(result.zipBytes()).isEqualTo(zip.length);
        assertThat(result.ciphertextBytes()).isEqualTo(archive.length);
        assertThat(result.ciphertextSha256()).isEqualTo(hash(archive));
        var members = tar(archive);
        assertThat(members.keySet())
                .containsExactly(
                        "part-000000000001.cms",
                        "part-000000000002.cms",
                        "part-000000000003.cms",
                        "part-000000000004.cms",
                        "part-000000000005.cms",
                        "part-000000000006.cms",
                        "part-000000000007.cms",
                        "part-000000000008.cms",
                        "part-000000000009.cms",
                        "part-000000000010.cms",
                        "manifest.json");
        var rejoined = new ByteArrayOutputStream();
        for (var member : members.entrySet())
            if (!member.getKey().equals("manifest.json")) rejoined.write(decrypt(member.getValue()));
        assertThat(rejoined.toByteArray()).isEqualTo(zip);
        assertThat(new String(members.get("manifest.json"), StandardCharsets.US_ASCII))
                .isEqualTo(
                        "{\"profile\":\"tar-cms-aes256gcm-rsa-oaep-sha256-v2\",\"parts\":10,"
                                + "\"partPlaintextBytes\":1000,\"zipBytes\":10000,\"zipSha256\":\""
                                + hash(zip)
                                + "\"}\n");
    }

    @Test
    void shouldEndThePartsOnAShortLastPart_whenTheZipIsNotAMultipleOfThePartSize()
            throws Exception {
        byte[] zip = new byte[2_500];
        new java.util.Random(11).nextBytes(zip);
        var output = new ByteArrayOutputStream();
        new CdsExportDeliveryComposer(100, 1000)
                .compose(
                        new ByteArrayInputStream(zip),
                        output,
                        recipient.certificate,
                        recipient.fingerprint,
                        zip.length,
                        hash(zip),
                        CdsExportRecipientFixture.NOW,
                        () -> {});
        var members = tar(output.toByteArray());
        assertThat(members).hasSize(4);
        assertThat(decrypt(members.get("part-000000000003.cms"))).hasSize(500);
    }

    @Test
    void shouldKeepOneCmsObject_forAZipWithinOneCipherMessage() throws Exception {
        byte[] zip = new byte[4096];
        var output = new ByteArrayOutputStream();
        var result =
                new CdsExportDeliveryComposer(4096, 1000)
                        .compose(
                                new ByteArrayInputStream(zip),
                                output,
                                recipient.certificate,
                                recipient.fingerprint,
                                zip.length,
                                hash(zip),
                                CdsExportRecipientFixture.NOW,
                                () -> {});
        assertThat(result.encryptionProfile()).isEqualTo(CdsExportDeliveryComposer.PROFILE);
        assertThat(decrypt(output.toByteArray())).isEqualTo(zip);
    }

    @Test
    void shouldRefuseAChangedZip_inAPartedDeliveryToo() throws Exception {
        byte[] zip = new byte[3000];
        for (long expected : new long[] {2999, 3001})
            assertThatThrownBy(
                            () ->
                                    new CdsExportDeliveryComposer(100, 1000)
                                            .compose(
                                                    new ByteArrayInputStream(zip),
                                                    new ByteArrayOutputStream(),
                                                    recipient.certificate,
                                                    recipient.fingerprint,
                                                    expected,
                                                    hash(zip),
                                                    CdsExportRecipientFixture.NOW,
                                                    () -> {}))
                    .hasMessage("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
    }

    @Test
    void shouldStopOnAGenuineOutputFailure_inAPartedDeliveryWithItsOwnMessage() {
        byte[] zip = new byte[3000];
        OutputStream full =
                new OutputStream() {
                    private int written;

                    @Override
                    public void write(int value) throws IOException {
                        write(new byte[] {(byte) value}, 0, 1);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        written += length;
                        if (written > 2000) throw new IOException("No space left on device");
                    }
                };
        assertThatThrownBy(
                        () ->
                                new CdsExportDeliveryComposer(100, 1000)
                                        .compose(
                                                new ByteArrayInputStream(zip),
                                                full,
                                                recipient.certificate,
                                                recipient.fingerprint,
                                                zip.length,
                                                hash(zip),
                                                CdsExportRecipientFixture.NOW,
                                                () -> {}))
                .hasMessage("No space left on device");
    }

    static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
