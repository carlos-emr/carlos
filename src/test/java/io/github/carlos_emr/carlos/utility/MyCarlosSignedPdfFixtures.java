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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;

/**
 * Builds the FAKE signed PDFs that myCarlos's verifier is tested against (carlos-emr/carlos#4691,
 * carlos-emr/mycarlos#55), in {@value #FIXTURE_PATH}.
 *
 * <p>The signing key is carlos-portal's {@code clinic_a} test key from
 * {@code tests/fixtures/delivery-contract.json}, derived the same way from the same seed text, so
 * myCarlos pins one clinic key for both fixtures. The PDFs are carried base64-encoded in the JSON
 * file: this repository keeps no binary fixtures. ECDSA signatures and PDF IDs are random, so a
 * regenerated file differs byte for byte; myCarlos pins the SHA-256 of a committed copy, and
 * {@code MyCarlosSignedPdfFixturesUnitTest} checks that every document is what its entry says.</p>
 *
 * <p>Regenerate from the repository root with {@code main}; nothing else writes the file.</p>
 *
 * @since 2026-10-21
 */
final class MyCarlosSignedPdfFixtures {

    static final String FIXTURE_PATH = "src/test/resources/pdfsigning/mycarlos-signed-pdf-fixtures.json";
    static final String DERIVATION = "key bytes = SHA-256(UTF-8 seed_text). P-256: scalar = int(key bytes, big-endian)"
            + " mod (n - 1) + 1. As in carlos-portal tests/fixtures/delivery-contract.json.";
    static final String CLINIC_A_SEED = "FAKE delivery contract test key: clinic-a signing key 1";
    static final String CLINIC_B_SEED = "FAKE delivery contract test key: clinic-b signing key 1";
    /** Seed of the RSA key used only by the {@code not_p256} case. */
    static final String RSA_SEED = "FAKE myCarlos fixture test key: RSA signer 1";
    static final String CLINIC_ID = "clinic-a";
    static final String RECIPIENT_ID = "rcp-FAKE0001aaaaaaaaaaaa";
    /** The FAKE password of the encrypted email copy: deliberately plain, not a secret. */
    static final String PASSWORD = "fake";
    static final String EXTRA_UPDATE_TITLE = "FAKE changed after signing";
    /** Bytes reserved for each CMS signature: room for an RSA-2048 one, a quarter of production's slot. */
    static final int SIGNATURE_SIZE = 8192;

    private MyCarlosSignedPdfFixtures() {
    }

    /** One fixture document. {@code manifest} and {@code password} may be null. */
    record Document(String name, String expect, byte[] pdf, byte[] manifest, String password, String note) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && args[0].isBlank())) {
            throw new IllegalArgumentException("Expected at most one nonblank fixture output path");
        }
        Path out = Path.of(args.length > 0 ? args[0] : FIXTURE_PATH);
        generate(out, MyCarlosSignedPdfFixtures::render);
        System.out.println("wrote " + out);
    }

    @FunctionalInterface
    interface Renderer {
        String render(Path work) throws Exception;
    }

    @FunctionalInterface
    interface AtomicReplacement {
        void replace(Path complete, Path destination) throws IOException;
    }

    static void generate(Path requested, Renderer renderer) throws Exception {
        generate(requested, renderer, (complete, destination) -> Files.move(complete, destination,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    /**
     * An explicit local operator path, not a request/upload path or a containment boundary.
     * Keep the selected location (including absolute paths) while using the shared trusted-path
     * resolver. No application directory is an appropriate sandbox for this developer CLI.
     */
    static Path outputPath(Path requested) {
        Path out = PathValidationUtils.resolveTrustedPath(requested.toFile(), "fixture output").toPath();
        if (out.getFileName() == null || out.getParent() == null || Files.isDirectory(out)) {
            throw new IllegalArgumentException("Fixture output must name a file");
        }
        return out;
    }

    static void generate(Path requested, Renderer renderer, AtomicReplacement replacement) throws Exception {
        Path out = outputPath(requested);
        Files.createDirectories(out.getParent());
        String json;
        try (OwnedPath work = new OwnedPath(Files.createTempDirectory("mycarlos-fixtures"))) {
            json = renderer.render(work.path());
        }
        // Clean the keys and work files before publication too: cleanup failure must leave the
        // previous fixture intact. The staging path is on the destination filesystem.
        try (OwnedPath complete = new OwnedPath(Files.createTempFile(out.getParent(), ".mycarlos-fixtures-", ".json"))) {
            Files.writeString(complete.path(), json, StandardCharsets.UTF_8);
            // Unsupported atomic moves fail; never truncate as a fallback.
            replacement.replace(complete.path(), out);
        }
    }

    /** Owns only a newly created temporary path; never follows links while removing its tree. */
    private record OwnedPath(Path path) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                return; // Published staging file was moved to its final destination.
            }
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    static String render(Path work) throws Exception {
        ensureBouncyCastle();
        KeyPair clinicA = p256FromSeed(CLINIC_A_SEED);
        X509Certificate clinicACertificate = certificate(clinicA, "SHA256withECDSA", "CN=FAKE clinic-a signing key 1,O=FAKE Demo Clinic");
        PDFSigningConfig signer = PdfSigningTestSupport.enabledConfig(work.resolve("clinic-a.p12"), clinicA.getPrivate(), clinicACertificate);
        KeyPair rsa = rsaFromSeed(RSA_SEED);
        X509Certificate rsaCertificate = certificate(rsa, "SHA256withRSA", "CN=FAKE RSA signer,O=FAKE Demo Clinic");
        PDFSigningConfig rsaSigner = PdfSigningTestSupport.enabledConfig(work.resolve("rsa.p12"), rsa.getPrivate(), rsaCertificate);
        Path blank = PdfSigningTestSupport.writeSinglePagePdf(work.resolve("blank.pdf"));
        try (OwnedPath encrypted = new OwnedPath(PDFEncryptionUtil.encryptPDF(blank, PASSWORD))) {

            String clinicBKid = kidFromSeed(CLINIC_B_SEED);
            List<Document> documents = new ArrayList<>();
            Signed send = sign(blank, signer, null, kid -> manifest(RECIPIENT_ID).toCanonicalJson(kid));
            documents.add(new Document("signed-send-to-mycarlos.pdf", "verified", send.pdf(), send.manifest(), null,
                    "A Send to myCarlos copy: unencrypted, PAdES B-B, manifest in the signature's update."));
            Signed email = sign(encrypted.path(), signer, PASSWORD, kid -> manifest(RECIPIENT_ID).toCanonicalJson(kid));
            documents.add(new Document("signed-email-encrypted.pdf", "verified", email.pdf(), email.manifest(), PASSWORD,
                    "The email path: AES-256 password-protected, then signed; the manifest is encrypted too."));
            Signed noManifest = sign(blank, signer, null, null);
            documents.add(new Document("signed-no-manifest.pdf", "signed_not_matched", noManifest.pdf(), null, null,
                    "Signed by the clinic, but there is no manifest to match a person."));
            Signed noRecipient = sign(blank, signer, null, kid -> manifest(null).toCanonicalJson(kid));
            documents.add(new Document("signed-no-recipient.pdf", "signed_not_matched", noRecipient.pdf(), noRecipient.manifest(), null,
                    "recipient_id is null: the patient had no portal link."));
            documents.add(new Document("refuse-byte-flipped.pdf", "bad_signature", flipCommentByte(send.pdf()), send.manifest(), null,
                    "One byte inside the signed range changed (in the header comment, so the PDF still opens)."));
            documents.add(new Document("refuse-extra-update.pdf", "extra_update", appendUpdate(send.pdf()), send.manifest(), null,
                    "A later incremental update changes the document information after the signature."));
            documents.add(new Document("refuse-incomplete-range.pdf", "incomplete_range",
                    concat(send.pdf(), "\n% FAKE bytes after the signed range\n".getBytes(StandardCharsets.US_ASCII)),
                    send.manifest(), null, "Bytes follow the signed range, but no update."));
            Signed kidMismatch = sign(blank, signer, null, kid -> manifest(RECIPIENT_ID).toCanonicalJson(clinicBKid));
            documents.add(new Document("refuse-kid-mismatch.pdf", "kid_mismatch", kidMismatch.pdf(), kidMismatch.manifest(), null,
                    "Signed with clinic-a's key, but the manifest names clinic-b's key."));
            Signed rsaSigned = sign(blank, rsaSigner, null, kid -> manifest(RECIPIENT_ID).toCanonicalJson(kid));
            documents.add(new Document("refuse-rsa-signer.pdf", "not_p256", rsaSigned.pdf(), rsaSigned.manifest(), null,
                    "Signed with an RSA key; the manifest's kid is that key's own ID."));
            Path sendFile = Files.write(work.resolve("send.pdf"), send.pdf());
            Signed twice = sign(sendFile, signer, null, null);
            documents.add(new Document("refuse-two-signatures.pdf", "two_signatures", twice.pdf(), send.manifest(), null,
                    "The Send to myCarlos copy, signed a second time."));
            Signed duplicate = sign(blank, signer, null,
                    kid -> withText(manifest(RECIPIENT_ID).toCanonicalJson(kid), "\"v\":1,", "\"v\":1,\"v\":1,"));
            documents.add(new Document("refuse-duplicate-member.pdf", "bad_manifest", duplicate.pdf(), duplicate.manifest(), null,
                    "The manifest repeats the member \"v\"."));
            Signed extraClaim = sign(blank, signer, null,
                    kid -> withText(manifest(RECIPIENT_ID).toCanonicalJson(kid), "\"version\":1}", "\"version\":1,\"zz_extra\":\"FAKE\"}"));
            documents.add(new Document("refuse-extra-claim.pdf", "bad_manifest", extraClaim.pdf(), extraClaim.manifest(), null,
                    "The manifest has one claim too many."));

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("v", 1);
            root.put("description", "FAKE signed PDFs for myCarlos's PDF verifier (carlos-emr/mycarlos#55), made by CARLOS's"
                    + " MyCarlosSignedPdfFixtures. Every key is FAKE. Fields ending _b64 are standard base64.");
            root.put("link", Map.of("clinic_id", CLINIC_ID, "recipient_id", RECIPIENT_ID, "signer_kid", kidFromSeed(CLINIC_A_SEED)));
            Map<String, Object> keys = new LinkedHashMap<>();
            keys.put("derivation", DERIVATION);
            keys.put("signer", ordered("clinic_id", CLINIC_ID, "seed_text", CLINIC_A_SEED,
                    "kid", PDFSigningUtil.keyId(clinicACertificate),
                    "spki_der_b64", b64(clinicA.getPublic().getEncoded()),
                    "cert_der_b64", b64(clinicACertificate.getEncoded())));
            keys.put("other_clinic", ordered("clinic_id", "clinic-b", "seed_text", CLINIC_B_SEED, "kid", kidFromSeed(CLINIC_B_SEED)));
            keys.put("rsa_signer", ordered("seed_text", RSA_SEED, "kid", PDFSigningUtil.keyId(rsaCertificate),
                    "cert_der_b64", b64(rsaCertificate.getEncoded()),
                    "note", "RSA-2048, used only by refuse-rsa-signer.pdf: generated by the JDK from SHA1PRNG seeded"
                            + " with SHA-256(seed_text), so it needs no stored key."));
            root.put("keys", keys);
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Document document : documents) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", document.name());
                entry.put("expect", document.expect());
                entry.put("note", document.note());
                if (document.password() != null) {
                    entry.put("password", document.password());
                }
                entry.put("byte_ranges", byteRanges(document.pdf(), document.password()));
                entry.put("manifest_bytes_b64", document.manifest() == null ? null : b64(document.manifest()));
                entry.put("pdf_b64", b64(document.pdf()));
                entries.add(entry);
            }
            root.put("documents", entries);
            return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root) + "\n";
        }
    }

    /** A signed PDF and the exact manifest bytes embedded in it, if any. */
    record Signed(byte[] pdf, byte[] manifest) {
    }

    static Signed sign(Path pdf, PDFSigningConfig config, String password, Function<String, byte[]> manifestJson)
            throws IOException {
        byte[][] embedded = new byte[1][];
        Function<String, byte[]> capture = manifestJson == null ? null : kid -> embedded[0] = manifestJson.apply(kid);
        Path signed = PDFSigningUtil.signPDFWithManifestJson(pdf, config, password, capture, SIGNATURE_SIZE);
        try {
            return new Signed(Files.readAllBytes(signed), embedded[0]);
        } finally {
            Files.deleteIfExists(signed);
        }
    }

    static DocumentManifest manifest(String recipientId) {
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) {
            secret[i] = (byte) (i + 1);
        }
        return new DocumentManifest(new DocumentIdCalculator(secret).documentId("document", "1001"), 1, 1_792_450_800L,
                CLINIC_ID, "FAKE Demo Clinic", "FAKE-Provider, Demo", recipientId, "FAKE-Jones, FAKE-Jacky", "1985-06-15",
                "FAKE CBC", "lab_report", "2026-10-21");
    }

    /** {@code b64url(SHA-256(SubjectPublicKeyInfo DER))} of the P-256 key derived from {@code seed}. */
    static String kidFromSeed(String seed) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(p256FromSeed(seed).getPublic().getEncoded()));
    }

    /**
     * An RSA-2048 key generated from a seeded SHA1PRNG, so no key is ever stored. Unlike the P-256
     * keys, which follow from their seed by a fixed formula, this one is only reproducible on the
     * same JDK and provider: how RSA key generation draws from the random source is not specified.
     * Regenerating the fixtures elsewhere may give {@code refuse-rsa-signer.pdf} a different key
     * and kid, which is fine, because its test only checks that the signer is RSA.
     */
    static KeyPair rsaFromSeed(String seed) throws Exception {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        return generator.generateKeyPair();
    }

    /** The P-256 key carlos-portal's fixture derives from {@code seed}. */
    static KeyPair p256FromSeed(String seed) throws Exception {
        ensureBouncyCastle();
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256r1");
        BigInteger scalar = new BigInteger(1, keyBytes).mod(curve.getN().subtract(BigInteger.ONE)).add(BigInteger.ONE);
        KeyFactory factory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        PrivateKey privateKey = factory.generatePrivate(new ECPrivateKeySpec(scalar, curve));
        PublicKey publicKey = factory.generatePublic(new ECPublicKeySpec(curve.getG().multiply(scalar).normalize(), curve));
        return new KeyPair(publicKey, privateKey);
    }

    private static X509Certificate certificate(KeyPair keyPair, String algorithm, String subject) throws Exception {
        X500Name name = new X500Name(subject);
        return new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(
                new JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date.from(Instant.parse("2026-01-01T00:00:00Z")),
                        Date.from(Instant.parse("2036-01-01T00:00:00Z")), name, keyPair.getPublic())
                        .build(new JcaContentSignerBuilder(algorithm).setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .build(keyPair.getPrivate())));
    }

    /** Flips one bit of the header's binary comment line: inside the signed range, ignored by parsers. */
    static byte[] flipCommentByte(byte[] pdf) {
        byte[] copy = pdf.clone();
        int lineEnd = 0;
        while (copy[lineEnd] != '\n') {
            lineEnd++;
        }
        if (copy[lineEnd + 1] != '%') {
            throw new IllegalStateException("No binary comment line after the PDF header");
        }
        copy[lineEnd + 2] ^= 0x01;
        return copy;
    }

    static byte[] appendUpdate(byte[] signed) throws IOException {
        try (PDDocument document = Loader.loadPDF(signed); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // PDFBox tracks the change itself and writes it as a new incremental update.
            document.getDocumentInformation().setTitle(EXTRA_UPDATE_TITLE);
            document.saveIncremental(out);
            return out.toByteArray();
        }
    }

    static List<List<Long>> byteRanges(byte[] pdf, String password) throws IOException {
        List<List<Long>> ranges = new ArrayList<>();
        try (PDDocument document = password == null ? Loader.loadPDF(pdf) : Loader.loadPDF(pdf, password)) {
            for (PDSignature signature : document.getSignatureDictionaries()) {
                List<Long> range = new ArrayList<>();
                for (int value : signature.getByteRange()) {
                    range.add((long) value);
                }
                ranges.add(range);
            }
        }
        return ranges;
    }

    private static byte[] withText(byte[] json, String target, String replacement) {
        String text = new String(json, StandardCharsets.UTF_8);
        if (!text.contains(target)) {
            throw new IllegalStateException("Manifest has no " + target);
        }
        return text.replace(target, replacement).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void ensureBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
