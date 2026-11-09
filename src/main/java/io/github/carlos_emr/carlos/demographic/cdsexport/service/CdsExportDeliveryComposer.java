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
 */
public final class CdsExportDeliveryComposer {
    public static final String PROFILE = "cms-aes256gcm-rsa-oaep-sha256-v1";
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

    public CdsExportDeliveryDto compose(InputStream validatedZip, OutputStream privateCiphertext,
            X509Certificate recipient, String pinnedFingerprint, long expectedZipBytes, String expectedZipSha256,
            Instant now, long maxCiphertextBytes, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(validatedZip); Objects.requireNonNull(privateCiphertext);
        Objects.requireNonNull(checkpoint); Objects.requireNonNull(now);
        if (expectedZipBytes < 1 || maxCiphertextBytes < 1 || expectedZipSha256 == null
                || !expectedZipSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("CDS_EXPORT_DELIVERY_INPUT_INVALID");
        validateRecipient(recipient, pinnedFingerprint, now);
        CountingOutput counted = new CountingOutput(privateCiphertext, maxCiphertextBytes, checkpoint);
        MessageDigest plainDigest = sha256(); long plainBytes = 0;
        try {
            var wrapper = new JceAsymmetricKeyWrapper(new OAEPParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT), recipient.getPublicKey()).setProvider(PROVIDER);
            var generator = new CMSAuthEnvelopedDataStreamGenerator(); generator.setBufferSize(65_536);
            generator.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(recipient, wrapper));
            var cipher = new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_GCM).setProvider(PROVIDER)
                    .setSecureRandom(new SecureRandom()).build();
            if (!(cipher instanceof OutputAEADEncryptor authenticated)) throw new IOException("CDS_EXPORT_DELIVERY_AEAD_UNAVAILABLE");
            try (OutputStream encrypted = generator.open(counted, authenticated)) {
                byte[] buffer = new byte[65_536]; int n;
                try {
                    while ((n = validatedZip.read(buffer)) != -1) {
                        checkpoint.run();
                        if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
                        if (n == 0) continue;
                        if (n > expectedZipBytes - plainBytes) throw new IOException("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
                        plainDigest.update(buffer, 0, n); plainBytes += n; encrypted.write(buffer, 0, n);
                    }
                } finally { java.util.Arrays.fill(buffer, (byte) 0); }
                if (plainBytes != expectedZipBytes || !HexFormat.of().formatHex(plainDigest.digest()).equals(expectedZipSha256)) {
                    throw new IOException("CDS_EXPORT_DELIVERY_ZIP_CHANGED");
                }
            }
            counted.flush(); checkpoint.run();
            return new CdsExportDeliveryDto(PROFILE, pinnedFingerprint, expectedZipSha256, plainBytes,
                    HexFormat.of().formatHex(counted.digest.digest()), counted.bytes);
        } catch (CMSException | GeneralSecurityException failure) {
            throw new IOException("CDS_EXPORT_DELIVERY_ENCRYPTION_FAILED");
        }
    }

    /** This checks a registered pin and current certificate constraints, not an unrequested public CA lookup. */
    public static void validateRecipient(X509Certificate certificate, String pinnedFingerprint, Instant now) throws IOException {
        if (certificate == null || pinnedFingerprint == null || !pinnedFingerprint.matches("[0-9a-f]{64}")) {
            throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_REQUIRED");
        }
        try {
            certificate.checkValidity(Date.from(now));
            if (!HexFormat.of().formatHex(sha256().digest(certificate.getEncoded())).equals(pinnedFingerprint)
                    || !(certificate.getPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048
                    || certificate.getBasicConstraints() >= 0) throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
            boolean[] usage = certificate.getKeyUsage();
            if (usage != null && (usage.length <= 2 || !usage[2])) throw new IOException("CDS_EXPORT_RECIPIENT_KEY_USAGE_INVALID");
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IOException("CDS_EXPORT_RECIPIENT_CERTIFICATE_INVALID");
        }
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static final class CountingOutput extends FilterOutputStream {
        // Per-operation incremental digest, owned by this stream/row accumulator; never shared singleton state.
        @SuppressWarnings("java:S9428")
        final MessageDigest digest = sha256();
        final long maximum;
        final Runnable checkpoint;
        long bytes;
        CountingOutput(OutputStream output, long maximum, Runnable checkpoint) {
            super(output); this.maximum = maximum; this.checkpoint = checkpoint;
        }
        @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] value, int offset, int length) throws IOException {
            checkpoint.run();
            if (Thread.currentThread().isInterrupted()) throw new IOException("CDS_EXPORT_CANCELLED");
            if (length > maximum - bytes) throw new IOException("CDS_EXPORT_DELIVERY_BYTE_LIMIT");
            out.write(value, offset, length); digest.update(value, offset, length); bytes += length;
        }
        @Override public void close() throws IOException { flush(); }
    }
}
