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

package io.github.carlos_emr.carlos.test.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import io.github.carlos_emr.carlos.utility.PDFSigningConfig;

/**
 * Shared fixture for the PDF signing tests: throwaway keys and certificates, a keystore-backed
 * {@link PDFSigningConfig}, sample PDFs, and independent verification of a detached signature.
 *
 * <p>One home for the verification path in particular. It carries a BouncyCastle workaround
 * that is easy to get wrong, and a second copy would diverge the next time BouncyCastle
 * changes.</p>
 *
 * @since 2026-09-21
 */
public final class PdfSigningTestSupport {

    public static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
    public static final String KEY_ALIAS = "pdf-signing";
    public static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    private PdfSigningTestSupport() {
    }

    public static void ensureBouncyCastleProvider() {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** Writes a one-page PDF at {@code pdf} and returns it. */
    public static Path writeSinglePagePdf(Path pdf) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        return pdf;
    }

    public static KeyPair createKeyPair(String algorithm, int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    /** A self-signed certificate valid from yesterday for a year, signed with the given algorithm. */
    public static X509Certificate createSelfSignedCertificate(KeyPair keyPair, String signingAlgorithm) throws Exception {
        ensureBouncyCastleProvider();
        X500Name subject = new X500Name("CN=CARLOS Test Signer");
        Instant now = Instant.now();
        X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic())
                .build(new JcaContentSignerBuilder(signingAlgorithm).setProvider(PROVIDER).build(keyPair.getPrivate()));
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(PROVIDER).getCertificate(holder);
        certificate.verify(keyPair.getPublic());
        return certificate;
    }

    /**
     * Writes a PKCS#12 keystore holding the key and certificate to {@code keystorePath} and
     * returns an enabled configuration that points at it.
     */
    public static PDFSigningConfig enabledConfig(Path keystorePath, PrivateKey privateKey, X509Certificate certificate)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEYSTORE_PASSWORD);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, KEYSTORE_PASSWORD, new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(keystorePath)) {
            keyStore.store(output, KEYSTORE_PASSWORD);
        }
        return new PDFSigningConfig(true, keystorePath.toString(), "PKCS12", KEYSTORE_PASSWORD, KEY_ALIAS, null,
                "CARLOS Test Signer", "Unit test signature", "Test Clinic", "test@example.com");
    }

    /** An enabled configuration backed by a fresh RSA-2048 self-signed key at {@code keystorePath}. */
    public static PDFSigningConfig enabledRsaConfig(Path keystorePath) throws Exception {
        ensureBouncyCastleProvider();
        KeyPair keyPair = createKeyPair("RSA", 2048);
        return enabledConfig(keystorePath, keyPair.getPrivate(), createSelfSignedCertificate(keyPair, "SHA256withRSA"));
    }

    /**
     * Verifies the first signature in a signed PDF against the certificate embedded in it,
     * using a verifier independent of the code under test.
     *
     * @param password the PDF's password, or null when it opens without one
     * @return true when the signature verifies over the signed byte range
     */
    public static boolean verifyDetachedSignature(Path signedPdf, String password) throws Exception {
        byte[] pdfBytes = Files.readAllBytes(signedPdf);
        try (PDDocument document = password == null
                ? Loader.loadPDF(signedPdf.toFile())
                : Loader.loadPDF(signedPdf.toFile(), password)) {
            PDSignature signature = document.getSignatureDictionaries().get(0);
            return verifyCms(signature.getContents(pdfBytes), signature.getSignedContent(pdfBytes));
        }
    }

    private static boolean verifyCms(byte[] signatureContents, byte[] signedContent) throws Exception {
        // A PDF reserves a fixed-size /Contents slot, so bytes follow the CMS blob: zeros in a
        // plain PDF, ciphertext in an encrypted one, where PDFBox encrypts the placeholder before
        // the signature is written over its front. Read from a stream so only the first DER
        // object is parsed: the byte[] constructor rejects that tail as "extra data" from
        // BouncyCastle 1.85 on.
        CMSSignedData signedData = new CMSSignedData(
                new CMSProcessableByteArray(signedContent), new ByteArrayInputStream(signatureContents));
        SignerInformation signer = signedData.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder certificate = (X509CertificateHolder)
                signedData.getCertificates().getMatches(signer.getSID()).iterator().next();
        try {
            return signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(PROVIDER).build(certificate));
        } catch (CMSException e) {
            return false;
        }
    }
}
