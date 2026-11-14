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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDeliveryDto;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAuthEnvelopedDataStreamGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputAEADEncryptor;
import org.bouncycastle.operator.jcajce.JceAsymmetricKeyWrapper;

/**
 * Streaming CMS authenticated enveloped delivery: AES-256-GCM and RSA-OAEP SHA-256/MGF1
 * SHA-256. The configured recipient certificate is pinned, not inferred from request bytes.
 * No private recipient key or password is retained. Use only owned private output: failures
 * can leave partial ciphertext that the caller must discard, never advertise or publish.
 * Caller owns both streams. A successful result binds the exact previously validated ZIP.
 *
 * <p>One AES-GCM message cannot carry more than 2^36 - 32 bytes: that is the cipher's own block
 * counter, not a budget. No package is refused for it. A ZIP of at most {@link
 * #SINGLE_MESSAGE_BYTES} is delivered as one CMS object ({@link #PROFILE}), exactly as before. A
 * larger one is delivered as a POSIX tar archive ({@link #PARTS_PROFILE}) of CMS objects of the
 * same profile, {@code part-000000000001.cms} onward, each carrying the next {@link #PART_BYTES}
 * of the ZIP under its own content-encryption key, followed by {@code manifest.json} (the part
 * count, the part size and the ZIP's length and SHA-256). The recipient decrypts the parts in
 * name order with the same command as a single object and concatenates the results. Nothing is
 * held in memory but one part.
 */
public final class CdsExportDeliveryComposer {
    public static final String PROFILE = "cms-aes256gcm-rsa-oaep-sha256-v1";

    /** A tar archive of {@link #PROFILE} objects; see the class description. */
    public static final String PARTS_PROFILE = "tar-cms-aes256gcm-rsa-oaep-sha256-v2";

    /** Every delivery profile this build writes and a retained artifact may carry. */
    public static final java.util.Set<String> PROFILES = java.util.Set.of(PROFILE, PARTS_PROFILE);

    /**
     * The largest ZIP delivered as one CMS object: half of what one AES-GCM message can carry, so
     * the cipher's limit is never approached.
     */
    public static final long SINGLE_MESSAGE_BYTES = 32L << 30;

    /** The ZIP bytes one part carries; one encrypted part is held in memory while it is written. */
    public static final int PART_BYTES = 16 << 20;

    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private final long singleMessageBytes;
    private final int partBytes;

    public CdsExportDeliveryComposer() {
        this(SINGLE_MESSAGE_BYTES, PART_BYTES);
    }

    /** Small thresholds let a test write a multi-part delivery from a few kilobytes. */
    CdsExportDeliveryComposer(long singleMessageBytes, int partBytes) {
        if (singleMessageBytes < 1 || singleMessageBytes > SINGLE_MESSAGE_BYTES || partBytes < 1)
            throw new IllegalArgumentException("CDS_EXPORT_DELIVERY_INPUT_INVALID");
        this.singleMessageBytes = singleMessageBytes;
        this.partBytes = partBytes;
    }

    public CdsExportDeliveryDto compose(
            InputStream validatedZip,
            OutputStream privateCiphertext,
            X509Certificate recipient,
            String pinnedFingerprint,
            long expectedZipBytes,
            String expectedZipSha256,
            Instant now,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(validatedZip);
        Objects.requireNonNull(privateCiphertext);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(now);
        if (expectedZipBytes < 1
                || expectedZipSha256 == null
                || !expectedZipSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_DELIVERY_INPUT_INVALID");
        validateRecipient(recipient, pinnedFingerprint, now);
        CountingOutput counted = new CountingOutput(privateCiphertext, checkpoint);
        MessageDigest plainDigest = sha256();
        boolean parts = expectedZipBytes > singleMessageBytes;
        long plainBytes = 0;
        try {
            byte[] buffer = new byte[65_536];
            try {
                if (!parts) {
                    try (OutputStream encrypted = open(counted, recipient)) {
                        int n;
                        while ((n = validatedZip.read(buffer)) != -1) {
                            probe(checkpoint);
                            if (n == 0) continue;
                            if (n > expectedZipBytes - plainBytes)
                                throw new IOException("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
                            plainDigest.update(buffer, 0, n);
                            plainBytes += n;
                            encrypted.write(buffer, 0, n);
                        }
                        // Checked before the object is closed, as it always was.
                        if (plainBytes != expectedZipBytes) changed();
                    }
                } else {
                    // One encrypted part in memory: the tar header needs its exact length.
                    var part = new java.io.ByteArrayOutputStream(partBytes + 65_536);
                    long count = 0;
                    boolean end = false;
                    while (!end) {
                        part.reset();
                        long partPlain = 0;
                        try (OutputStream encrypted = open(part, recipient)) {
                            while (partPlain < partBytes) {
                                probe(checkpoint);
                                int n =
                                        validatedZip.read(
                                                buffer,
                                                0,
                                                (int) Math.min(buffer.length, partBytes - partPlain));
                                if (n == -1) {
                                    end = true;
                                    break;
                                }
                                if (n == 0) continue;
                                if (n > expectedZipBytes - plainBytes) changed();
                                plainDigest.update(buffer, 0, n);
                                plainBytes += n;
                                partPlain += n;
                                encrypted.write(buffer, 0, n);
                            }
                        }
                        // A ZIP that is an exact multiple of the part size ends on an empty read.
                        if (partPlain == 0) break;
                        count = Math.addExact(count, 1);
                        member(
                                counted,
                                String.format(java.util.Locale.ROOT, "part-%012d.cms", count),
                                part.toByteArray(),
                                now);
                    }
                    if (plainBytes != expectedZipBytes) changed();
                    member(
                            counted,
                            "manifest.json",
                            ("{\"profile\":\""
                                            + PARTS_PROFILE
                                            + "\",\"parts\":"
                                            + count
                                            + ",\"partPlaintextBytes\":"
                                            + partBytes
                                            + ",\"zipBytes\":"
                                            + expectedZipBytes
                                            + ",\"zipSha256\":\""
                                            + expectedZipSha256
                                            + "\"}\n")
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                            now);
                    // The end of a tar archive is two zero blocks.
                    counted.write(new byte[1024], 0, 1024);
                }
            } finally {
                java.util.Arrays.fill(buffer, (byte) 0);
            }
            if (plainBytes != expectedZipBytes
                    || !HexFormat.of().formatHex(plainDigest.digest()).equals(expectedZipSha256))
                changed();
            counted.flush();
            checkpoint.run();
            return new CdsExportDeliveryDto(
                    parts ? PARTS_PROFILE : PROFILE,
                    pinnedFingerprint,
                    expectedZipSha256,
                    plainBytes,
                    HexFormat.of().formatHex(counted.digest.digest()),
                    counted.bytes);
        } catch (CMSException | GeneralSecurityException failure) {
            throw new IOException("CDS_EXPORT_DELIVERY_ENCRYPTION_FAILED");
        }
    }

    /** One CMS object to the pinned recipient, under a fresh content-encryption key. */
    private static OutputStream open(OutputStream target, X509Certificate recipient)
            throws IOException, CMSException, GeneralSecurityException {
        var wrapper =
                new JceAsymmetricKeyWrapper(
                                new OAEPParameterSpec(
                                        "SHA-256",
                                        "MGF1",
                                        MGF1ParameterSpec.SHA256,
                                        PSource.PSpecified.DEFAULT),
                                recipient.getPublicKey())
                        .setProvider(PROVIDER);
        var generator = new CMSAuthEnvelopedDataStreamGenerator();
        generator.setBufferSize(65_536);
        generator.addRecipientInfoGenerator(
                new JceKeyTransRecipientInfoGenerator(recipient, wrapper));
        var cipher =
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM)
                        .setProvider(PROVIDER)
                        .setSecureRandom(new SecureRandom())
                        .build();
        if (!(cipher instanceof OutputAEADEncryptor authenticated))
            throw new IOException("CDS_EXPORT_DELIVERY_AEAD_UNAVAILABLE");
        return generator.open(target, authenticated);
    }

    /** One regular file of a POSIX ustar archive, readable only by its owner. */
    private static void member(OutputStream tar, String name, byte[] content, Instant now)
            throws IOException {
        byte[] header = new byte[512];
        field(header, 0, name);
        field(header, 100, "0000600");
        field(header, 108, "0000000");
        field(header, 116, "0000000");
        field(header, 124, String.format(java.util.Locale.ROOT, "%011o", content.length));
        field(
                header,
                136,
                String.format(java.util.Locale.ROOT, "%011o", Math.max(0, now.getEpochSecond())));
        java.util.Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        field(header, 257, "ustar");
        header[263] = '0';
        header[264] = '0';
        int sum = 0;
        for (byte value : header) sum += value & 255;
        field(header, 148, String.format(java.util.Locale.ROOT, "%06o", sum));
        header[154] = 0;
        header[155] = ' ';
        tar.write(header, 0, 512);
        tar.write(content, 0, content.length);
        int padding = (512 - content.length % 512) % 512;
        if (padding > 0) tar.write(new byte[padding], 0, padding);
    }

    private static void field(byte[] header, int offset, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, bytes.length);
    }

    private static void probe(Runnable checkpoint) throws IOException {
        checkpoint.run();
        if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
    }

    private static void changed() throws IOException {
        throw new IOException("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
    }

    /** This checks a registered pin and current certificate constraints, not an unrequested public CA lookup. */
    public static void validateRecipient(
            X509Certificate certificate, String pinnedFingerprint, Instant now) throws IOException {
        if (certificate == null
                || pinnedFingerprint == null
                || !pinnedFingerprint.matches("[0-9a-f]{64}")) {
            throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_REQUIRED");
        }
        try {
            certificate.checkValidity(Date.from(now));
            if (!HexFormat.of()
                            .formatHex(sha256().digest(certificate.getEncoded()))
                            .equals(pinnedFingerprint)
                    || !(certificate.getPublicKey() instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() < 2048
                    || certificate.getBasicConstraints() >= 0)
                throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
            boolean[] usage = certificate.getKeyUsage();
            if (usage != null && (usage.length <= 2 || !usage[2]))
                throw new IOException("CDS_EXPORT_RECIPIENT_KEY_USAGE_INVALID");
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class CountingOutput extends FilterOutputStream {
        // Per-operation incremental digest, owned by this stream/row accumulator; never shared
        // singleton state.
        @SuppressWarnings("java:S9428")
        final MessageDigest digest = sha256();

        final Runnable checkpoint;
        long bytes;

        CountingOutput(OutputStream output, Runnable checkpoint) {
            super(output);
            this.checkpoint = checkpoint;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            checkpoint.run();
            if (Thread.currentThread().isInterrupted())
                throw new IOException("CDS_EXPORT_CANCELLED");
            // No delivery size is refused; a genuine write failure is the output's own.
            out.write(value, offset, length);
            digest.update(value, offset, length);
            bytes = Math.addExact(bytes, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
