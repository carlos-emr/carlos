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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that every document in the committed myCarlos fixture file is what its entry says, so
 * myCarlos's verifier tests (carlos-emr/mycarlos#55) rest on PDFs CARLOS itself signed.
 */
@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("myCarlos signed PDF fixtures")
class MyCarlosSignedPdfFixturesUnitTest {

    private static JsonNode fixture;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = new ObjectMapper().readTree(Files.readString(Path.of(MyCarlosSignedPdfFixtures.FIXTURE_PATH)));
        if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            java.security.Security.addProvider(new BouncyCastleProvider());
        }
    }

    static Stream<JsonNode> documents() throws IOException {
        loadFixture();
        List<JsonNode> all = new ArrayList<>();
        fixture.get("documents").forEach(all::add);
        return all.stream();
    }

    @Test
    @DisplayName("should sign with carlos-portal's clinic-a test key, derived from its seed text")
    void shouldUseThePortalClinicKey_forTheSigner() throws Exception {
        JsonNode signer = fixture.get("keys").get("signer");
        byte[] spki = MyCarlosSignedPdfFixtures.p256FromSeed(signer.get("seed_text").asText()).getPublic().getEncoded();

        assertThat(signer.get("seed_text").asText()).isEqualTo(MyCarlosSignedPdfFixtures.CLINIC_A_SEED);
        assertThat(Base64.getDecoder().decode(signer.get("spki_der_b64").asText())).isEqualTo(spki);
        // The same seed text as carlos-portal's delivery-contract.json, so the same key and kid.
        assertThat(signer.get("kid").asText()).isEqualTo(kid(spki));
        assertThat(certificateSpki(signer.get("cert_der_b64").asText())).isEqualTo(spki);
        assertThat(kid(MyCarlosSignedPdfFixtures.p256FromSeed(MyCarlosSignedPdfFixtures.CLINIC_B_SEED).getPublic().getEncoded()))
                .isEqualTo(fixture.get("keys").get("other_clinic").get("kid").asText());
        assertThat(fixture.get("link").get("signer_kid").asText()).isEqualTo(kid(spki));
    }

    @Test
    @DisplayName("should cover every agreed outcome with distinct file names")
    void shouldCoverEveryOutcome_withDistinctNames() throws IOException {
        List<String> names = documents().map(d -> d.get("name").asText()).toList();
        List<String> expects = documents().map(d -> d.get("expect").asText()).distinct().toList();

        assertThat(names).doesNotHaveDuplicates().allMatch(name -> name.matches("[a-z0-9-]+\\.pdf"));
        assertThat(expects).containsExactlyInAnyOrder("verified", "signed_not_matched", "bad_signature", "extra_update",
                "incomplete_range", "kid_mismatch", "not_p256", "two_signatures", "bad_manifest");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documents")
    @DisplayName("should hold a PDF that is what its entry says")
    void shouldMatchItsEntry_forEveryDocument(JsonNode entry) throws Exception {
        byte[] pdf = Base64.getDecoder().decode(entry.get("pdf_b64").asText());
        String password = entry.hasNonNull("password") ? entry.get("password").asText() : null;
        byte[] declaredManifest = entry.get("manifest_bytes_b64").isNull() ? null
                : Base64.getDecoder().decode(entry.get("manifest_bytes_b64").asText());
        Inspection pdfFacts = inspect(pdf, password);
        String expect = entry.get("expect").asText();
        String signerSpki = fixture.get("keys").get("signer").get("spki_der_b64").asText();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdfFacts.ranges()).isEqualTo(declaredRanges(entry));
        // Strict, as myCarlos checks: only zeros follow the CMS in /Contents, never ciphertext.
        assertThat(pdfFacts.signatures()).allMatch(Sig::zeroPadded);
        if (!"two_signatures".equals(expect)) {
            assertThat(pdfFacts.signatures()).hasSize(1);
        }
        // What every document but the refused ones claims: the manifest the entry lists, embedded as is.
        if (!"two_signatures".equals(expect)) {
            assertThat(pdfFacts.manifest()).isEqualTo(declaredManifest);
            // And the signer binds exactly those bytes with its signed manifest-digest attribute.
            byte[] boundDigest = signedManifestDigest(pdf, password);
            if (declaredManifest == null) {
                assertThat(boundDigest).isNull();
            } else {
                assertThat(boundDigest).isEqualTo(MessageDigest.getInstance("SHA-256").digest(declaredManifest));
            }
        }
        Sig only = pdfFacts.signatures().get(0);
        switch (expect) {
            case "verified" -> {
                assertWholeFileAndValid(only, pdf);
                assertThat(only.subFilter()).isEqualTo(PDSignature.SUBFILTER_ETSI_CADES_DETACHED.getName());
                assertThat(only.signerSpki()).isEqualTo(signerSpki);
                JsonNode manifest = new ObjectMapper().readTree(declaredManifest);
                assertThat(manifest.get("kid").asText()).isEqualTo(MyCarlosSignedPdfFixtures.kidFromSeed(MyCarlosSignedPdfFixtures.CLINIC_A_SEED));
                assertThat(manifest.get("recipient_id").asText()).isEqualTo(MyCarlosSignedPdfFixtures.RECIPIENT_ID);
                assertThat(manifest.get("clinic_id").asText()).isEqualTo(MyCarlosSignedPdfFixtures.CLINIC_ID);
                assertThat(isExactManifest(declaredManifest)).as("accepted as a manifest").isTrue();
            }
            case "signed_not_matched" -> {
                assertWholeFileAndValid(only, pdf);
                assertThat(declaredManifest == null || new ObjectMapper().readTree(declaredManifest).get("recipient_id").isNull())
                        .as("no manifest, or a manifest with no recipient").isTrue();
                assertThat(declaredManifest == null || isExactManifest(declaredManifest)).as("accepted as a manifest").isTrue();
            }
            case "bad_signature" -> {
                assertThat(only.coversWholeFile(pdf.length)).isTrue();
                assertThat(only.cmsValid()).isFalse();
            }
            case "extra_update" -> {
                assertThat(only.cmsValid()).isTrue();
                assertThat(only.coversWholeFile(pdf.length)).isFalse();
                assertThat(tailAfter(only, pdf)).contains("startxref");
                assertThat(pdfFacts.title()).isEqualTo(MyCarlosSignedPdfFixtures.EXTRA_UPDATE_TITLE);
            }
            case "incomplete_range" -> {
                assertThat(only.cmsValid()).isTrue();
                assertThat(only.coversWholeFile(pdf.length)).isFalse();
                assertThat(tailAfter(only, pdf)).doesNotContain("startxref").doesNotContain("xref");
            }
            case "kid_mismatch" -> {
                assertWholeFileAndValid(only, pdf);
                assertThat(only.signerSpki()).isEqualTo(signerSpki);
                assertThat(new ObjectMapper().readTree(declaredManifest).get("kid").asText())
                        .isEqualTo(MyCarlosSignedPdfFixtures.kidFromSeed(MyCarlosSignedPdfFixtures.CLINIC_B_SEED));
                // Well formed: only the key ID is wrong.
                assertThat(isExactManifest(declaredManifest)).as("accepted as a manifest").isTrue();
            }
            case "not_p256" -> {
                assertWholeFileAndValid(only, pdf);
                assertThat(only.keyAlgorithm()).isEqualTo("RSA");
            }
            case "two_signatures" -> {
                assertThat(pdfFacts.signatures()).hasSize(2);
                assertThat(pdfFacts.signatures()).allMatch(Sig::cmsValid);
                assertThat(pdfFacts.signatures().get(1).coversWholeFile(pdf.length)).isTrue();
            }
            case "bad_manifest" -> {
                assertWholeFileAndValid(only, pdf);
                assertThat(isExactManifest(declaredManifest)).as("refused as a manifest").isFalse();
            }
            default -> throw new AssertionError("Unknown expect value " + expect);
        }
    }

    @Test
    @DisplayName("should open the encrypted email copy only with its password")
    void shouldNeedThePassword_forTheEncryptedCopy() throws IOException {
        JsonNode entry = documents().filter(d -> d.get("name").asText().equals("signed-email-encrypted.pdf")).findFirst().orElseThrow();
        byte[] pdf = Base64.getDecoder().decode(entry.get("pdf_b64").asText());

        assertThatThrownBy(() -> Loader.loadPDF(pdf).close()).isInstanceOf(IOException.class);
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).doesNotContain("FAKE-Jones");
    }

    private static void assertWholeFileAndValid(Sig signature, byte[] pdf) {
        assertThat(signature.cmsValid()).as("signature verifies").isTrue();
        assertThat(signature.coversWholeFile(pdf.length)).as("byte range covers the whole file").isTrue();
    }

    /** A manifest myCarlos accepts: strict JSON with no duplicate member and exactly the agreed claims. */
    private static boolean isExactManifest(byte[] json) {
        ObjectMapper strict = new ObjectMapper();
        strict.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try {
            JsonNode node = strict.readTree(json);
            List<String> claims = new ArrayList<>();
            node.fieldNames().forEachRemaining(claims::add);
            return claims.equals(List.of("clinic_id", "clinic_name", "document", "document_id", "issued_at", "kid",
                    "patient", "provider_name", "recipient_id", "typ", "v", "version"));
        } catch (IOException e) {
            return false;
        }
    }

    private static List<List<Long>> declaredRanges(JsonNode entry) {
        List<List<Long>> ranges = new ArrayList<>();
        for (JsonNode range : entry.get("byte_ranges")) {
            List<Long> values = new ArrayList<>();
            range.forEach(value -> values.add(value.asLong()));
            ranges.add(values);
        }
        return ranges;
    }

    private static String tailAfter(Sig signature, byte[] pdf) {
        int end = (int) (signature.range().get(2) + signature.range().get(3));
        return new String(pdf, end, pdf.length - end, StandardCharsets.ISO_8859_1);
    }

    /** The signed manifest-digest attribute of the first signature, or {@code null} when absent. */
    private static byte[] signedManifestDigest(byte[] pdf, String password) throws Exception {
        try (PDDocument document = password == null ? Loader.loadPDF(pdf) : Loader.loadPDF(pdf, password)) {
            PDSignature signature = document.getSignatureDictionaries().get(0);
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(pdf)),
                    new ByteArrayInputStream(signature.getContents(pdf)));
            SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
            ASN1EncodableVector found = signer.getSignedAttributes().getAll(PDFSigningUtil.DOCUMENT_MANIFEST_SHA256);
            if (found.size() == 0) {
                return null;
            }
            assertThat(found.size()).isEqualTo(1);
            Attribute attribute = Attribute.getInstance(found.get(0));
            assertThat(attribute.getAttrValues().size()).isEqualTo(1);
            return ASN1OctetString.getInstance(attribute.getAttrValues().getObjectAt(0)).getOctets();
        }
    }

    record Sig(List<Long> range, boolean cmsValid, String subFilter, String signerSpki, String keyAlgorithm,
            boolean zeroPadded) {
        boolean coversWholeFile(long length) {
            return range.get(0) == 0 && range.get(2) + range.get(3) == length;
        }
    }

    record Inspection(List<Sig> signatures, byte[] manifest, String title) {
        List<List<Long>> ranges() {
            return signatures.stream().map(Sig::range).toList();
        }
    }

    private static Inspection inspect(byte[] pdf, String password) throws Exception {
        List<Sig> signatures = new ArrayList<>();
        byte[] manifest = null;
        try (PDDocument document = password == null ? Loader.loadPDF(pdf) : Loader.loadPDF(pdf, password)) {
            for (PDSignature signature : document.getSignatureDictionaries()) {
                List<Long> range = new ArrayList<>();
                for (int value : signature.getByteRange()) {
                    range.add((long) value);
                }
                // Read the CMS from a stream: the /Contents slot is padded past the DER object.
                CMSSignedData signed = new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(pdf)),
                        new ByteArrayInputStream(signature.getContents(pdf)));
                SignerInformation signer = signed.getSignerInfos().getSigners().iterator().next();
                X509CertificateHolder certificate = (X509CertificateHolder) signed.getCertificates()
                        .getMatches(signer.getSID()).iterator().next();
                boolean valid;
                try {
                    valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(certificate));
                } catch (CMSException e) {
                    valid = false;
                }
                byte[] contents = signature.getContents(pdf);
                int cmsLength;
                try (ASN1InputStream input = new ASN1InputStream(contents)) {
                    cmsLength = input.readObject().getEncoded().length;
                }
                boolean zeroPadded = true;
                for (int i = cmsLength; i < contents.length; i++) {
                    zeroPadded &= contents[i] == 0;
                }
                signatures.add(new Sig(range, valid, signature.getSubFilter(),
                        Base64.getEncoder().encodeToString(certificate.getSubjectPublicKeyInfo().getEncoded()),
                        new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .getCertificate(certificate).getPublicKey().getAlgorithm(), zeroPadded));
            }
            PDDocumentNameDictionary names = document.getDocumentCatalog().getNames();
            if (names != null && names.getEmbeddedFiles() != null && names.getEmbeddedFiles().getNames() != null) {
                Map<String, PDComplexFileSpecification> files = names.getEmbeddedFiles().getNames();
                PDComplexFileSpecification spec = files.get(DocumentManifest.FILE_NAME);
                if (spec != null) {
                    manifest = spec.getEmbeddedFile().toByteArray();
                }
            }
            return new Inspection(signatures, manifest, document.getDocumentInformation().getTitle());
        }
    }

    private static byte[] certificateSpki(String certificateB64) throws Exception {
        return new X509CertificateHolder(Base64.getDecoder().decode(certificateB64)).getSubjectPublicKeyInfo().getEncoded();
    }

    private static String kid(byte[] spki) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(spki));
    }
}
