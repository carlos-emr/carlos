/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.apache.pdfbox.Loader;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

/** Existing PAdES producer, with an explicit same-key/manifest check before durable staging. */
final class PortalMyCarlosSignedOriginal {
    private PortalMyCarlosSignedOriginal() {}

    static byte[] sign(Path rendered, DocumentManifest manifest, PDFSigningConfig config,
            PatientPortalClinicKeysPublishRequest.Key identity, EmailComposeWorkingDirectory working) throws IOException {
        try {
            Path source = PathValidationUtils.validateConfiguredFile(rendered.toString(), "myCarlos rendered PDF").toPath();
            Path signed = PDFSigningUtil.signPDF(source, config, null, manifest);
            Path owned = working.adoptGeneratedPdf(signed);
            Path checked = PathValidationUtils.validateConfiguredFile(owned.toString(), "myCarlos signed original").toPath();
            byte[] bytes;
            try (var input = Files.newInputStream(checked)) {
                bytes = input.readNBytes(PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES + 1);
            }
            verify(bytes, manifest, identity);
            return bytes;
        } catch (Exception failure) {
            throw new IOException("myCarlos signed original could not be prepared");
        }
    }

    static void verify(byte[] bytes, DocumentManifest manifest, PatientPortalClinicKeysPublishRequest.Key identity) throws IOException {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES
                    || manifest == null || identity == null || !"active".equals(identity.status())) throw new IOException();
            try (var pdf = Loader.loadPDF(bytes)) {
                if (pdf.isEncrypted() || pdf.getSignatureDictionaries().size() != 1) throw new IOException();
                var signature = pdf.getSignatureDictionaries().getFirst();
                int[] range = signature.getByteRange();
                if (!"ETSI.CAdES.detached".equals(signature.getSubFilter()) || range == null || range.length != 4
                        || range[0] != 0 || range[1] <= 0 || range[2] <= range[1] || range[3] <= 0
                        || (long) range[2] + range[3] != bytes.length) throw new IOException();
                var cms = new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(bytes)), new java.io.ByteArrayInputStream(signature.getContents(bytes)));
                if (cms.getSignerInfos().size() != 1) throw new IOException();
                var signer = cms.getSignerInfos().getSigners().iterator().next();
                var matches = cms.getCertificates().getMatches(signer.getSID());
                if (matches.size() != 1) throw new IOException();
                X509CertificateHolder certificate = (X509CertificateHolder) matches.iterator().next();
                byte[] expectedSpki = Base64.getUrlDecoder().decode(identity.spki());
                if (!Arrays.equals(expectedSpki, certificate.getSubjectPublicKeyInfo().getEncoded())
                        || !signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider()).build(certificate))) throw new IOException();
                var names = pdf.getDocumentCatalog().getNames();
                if (names == null || names.getEmbeddedFiles() == null || names.getEmbeddedFiles().getNames() == null) throw new IOException();
                var specification = names.getEmbeddedFiles().getNames().get(DocumentManifest.FILE_NAME);
                if (specification == null || specification.getEmbeddedFile() == null) throw new IOException();
                byte[] expected = manifest.toCanonicalJson(identity.kid());
                if (expected.length > 32768) throw new IOException();
                try (var input = specification.getEmbeddedFile().createInputStream()) {
                    if (!Arrays.equals(expected, input.readNBytes(expected.length + 1))) throw new IOException();
                }
            }
        } catch (Exception failure) {
            throw new IOException("myCarlos signed original does not match the selected patient and signing key");
        }
    }
}
