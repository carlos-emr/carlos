/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Applies certificate-backed signatures to PDF files as PAdES baseline B-B: SubFilter
 * {@code ETSI.CAdES.detached}, a detached CMS signature whose signed attributes carry the content
 * type, the message digest and an ESS signing-certificate-v2 for the signer, and no CMS
 * signing-time (the signing time is the signature dictionary's {@code /M} entry).
 *
 * <p>RSA and EC keys both sign. myCarlos verifies ECDSA P-256 signatures (carlos-emr/carlos#4691,
 * carlos-emr/mycarlos#55), so a clinic that wants its PDFs shown as verified there uses a P-256
 * key.</p>
 *
 * @since 2026-09-21
 */
@SuppressWarnings("java:S2143") // PDFBox PDSignature.setSignDate takes java.util.Calendar; the boundary adapts from java.time.
public final class PDFSigningUtil {
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final int PREFERRED_SIGNATURE_SIZE = 32768;

    /**
     * id-aa-carlos-documentManifestSha256 (v1): the signed attribute that carries SHA-256 of the
     * embedded document manifest's exact bytes, as one 32-byte OCTET STRING. Its OID is under the
     * UUID arc of ITU-T X.667 (2.25), which needs no registration. myCarlos refuses a signature
     * with more than one such attribute or value, or a value that is not 32 bytes long.
     */
    static final ASN1ObjectIdentifier DOCUMENT_MANIFEST_SHA256 =
            new ASN1ObjectIdentifier("2.25.132515939282652475535231260060469025505");

    private PDFSigningUtil() {
    }

    /**
     * Signs a PDF that opens without a password. See
     * {@link #signPDF(Path, PDFSigningConfig, String)} for the contract.
     */
    public static Path signPDF(Path pdfPath, PDFSigningConfig config) throws IOException {
        return signPDF(pdfPath, config, null);
    }

    /**
     * Adds a detached CMS signature to a PDF, leaving the source file untouched.
     *
     * <p>The result is always a <em>new</em> owner-only temp file under {@code java.io.tmpdir}
     * that the caller must adopt into a working directory or delete; on any failure that file
     * is already removed. Deciding whether to sign at all is the caller's job: this refuses a
     * disabled configuration rather than handing the input back, so a returned path can only
     * ever mean "you own this file".</p>
     *
     * @param pdfPath the PDF to sign; opened read-only, never modified
     * @param config signing configuration; must be enabled and complete
     * @param ownerPassword password to open the PDF with if it will not open without one, for
     *        example the send passphrase of a PDF CARLOS encrypted; ignored otherwise
     * @return the signed temp file
     * @throws IOException when the keystore cannot be loaded or validated, the PDF cannot be
     *         opened (including a wrong or missing password), or signing fails
     * @throws IllegalStateException when signing is not enabled, or is enabled but incomplete
     */
    public static Path signPDF(Path pdfPath, PDFSigningConfig config, String ownerPassword) throws IOException {
        return signPDF(pdfPath, config, ownerPassword, null);
    }

    /**
     * Like {@link #signPDF(Path, PDFSigningConfig, String)}, and also embeds {@code manifest} as
     * {@value DocumentManifest#FILE_NAME}, with {@code kid} set from the signing certificate.
     *
     * <p>The manifest goes into the same incremental update as the signature. An encrypted PDF is
     * opened with its owner password, so PDFBox encrypts the new objects like the rest of the file,
     * and the signature, whose byte range runs from byte 0 to the end, covers the manifest. The file
     * is listed in the {@code EmbeddedFiles} name tree and in the catalog's {@code /AF} array with
     * {@code /AFRelationship /Data}.</p>
     *
     * @param manifest the manifest to embed, or {@code null} to sign without one
     */
    public static Path signPDF(Path pdfPath, PDFSigningConfig config, String ownerPassword, DocumentManifest manifest)
            throws IOException {
        if (pdfPath == null) {
            throw new IOException("PDF path is required for signing");
        }
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException("PDF signing is not enabled");
        }
        config.validateEnabled();
        ensureBouncyCastleProvider();

        SigningMaterial signingMaterial = loadSigningMaterial(config);
        // The exact bytes embedded below, and what the signed manifest-digest attribute names.
        byte[] manifestJson = manifest == null
                ? null : manifest.toCanonicalJson(keyId(signingMaterial.signingCertificate()));
        Path signedPDFPath = PathValidationUtils.createSecureTempFile(
                PathValidationUtils.validateGeneratedFileName("signedPDF_" + System.currentTimeMillis()), ".pdf").toPath();

        CmsDetachedSignature cms = new CmsDetachedSignature(signingMaterial, manifestDigest(manifestJson));
        int[] byteRange;
        boolean encrypted;
        try (PDDocument document = loadPDF(pdfPath, ownerPassword);
             SignatureOptions signatureOptions = new SignatureOptions();
             OutputStream output = Files.newOutputStream(signedPDFPath)) {
            signatureOptions.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName(config.getSignerName());
            signature.setReason(config.getReason());
            if (config.getLocation() != null) {
                signature.setLocation(config.getLocation());
            }
            if (config.getContact() != null) {
                signature.setContactInfo(config.getContact());
            }
            signature.setSignDate(GregorianCalendar.from(ZonedDateTime.now(ZoneId.systemDefault())));

            if (manifestJson != null) {
                embedManifest(document, manifestJson);
            }
            document.addSignature(signature, cms, signatureOptions);
            document.saveIncremental(output);
            encrypted = document.isEncrypted();
            byteRange = signature.getByteRange();
        } catch (IOException | RuntimeException e) {
            // RuntimeException too: PDFBox refuses an empty document with IllegalStateException,
            // and an output left behind here is never adopted by a working directory.
            deletePartialSignedPDF(signedPDFPath, e);
            throw new IOException("Failed to sign PDF document", e);
        }
        if (encrypted) {
            try {
                zeroSignaturePadding(signedPDFPath, byteRange, cms.signatureLength());
            } catch (IOException | RuntimeException e) {
                deletePartialSignedPDF(signedPDFPath, e);
                throw new IOException("Failed to sign PDF document", e);
            }
        }
        return signedPDFPath;
    }

    /**
     * Rewrites the reserved space after the CMS signature in {@code /Contents} as zeros.
     *
     * <p>In an encrypted document's incremental save, PDFBox 3 encrypts the zero placeholder it
     * reserves for {@code /Contents} and then writes the plain CMS over its start, so ciphertext
     * follows the signature. ISO 32000-1 7.6.1 never encrypts a signature's {@code /Contents}, and
     * strict verifiers such as myCarlos's require zeros after the CMS. The hex string lies between
     * the two signed byte ranges, so rewriting it leaves the signature as it was.</p>
     *
     * @param byteRange the signature's {@code [0, gapStart, gapEnd, length]}; the gap holds
     *        {@code <hex>} from {@code gapStart} to {@code gapEnd - 1}
     * @param cmsLength the length of the CMS written at the start of the gap, in bytes
     * @throws IOException when the byte range does not frame a {@code <...>} string large enough
     *         for the CMS, so the signed file is refused rather than patched blindly
     */
    static void zeroSignaturePadding(Path signedPdf, int[] byteRange, int cmsLength) throws IOException {
        if (byteRange == null || byteRange.length != 4 || byteRange[0] != 0 || cmsLength <= 0) {
            throw new IOException("Unexpected signature byte range");
        }
        long open = byteRange[1];
        long close = byteRange[2] - 1L;
        long firstPadding = open + 1 + 2L * cmsLength;
        try (FileChannel channel = FileChannel.open(signedPdf, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer delimiter = ByteBuffer.allocate(1);
            channel.read(delimiter, open);
            boolean opens = delimiter.get(0) == '<';
            delimiter.clear();
            channel.read(delimiter, close);
            if (!opens || delimiter.get(0) != '>' || firstPadding > close) {
                throw new IOException("Signature byte range does not frame the /Contents string");
            }
            byte[] zeros = new byte[(int) (close - firstPadding)];
            Arrays.fill(zeros, (byte) '0');
            ByteBuffer padding = ByteBuffer.wrap(zeros);
            while (padding.hasRemaining()) {
                channel.write(padding, firstPadding + padding.position());
            }
        }
    }

    /** SHA-256 of the manifest bytes for the signed attribute, or {@code null} without a manifest. */
    private static byte[] manifestDigest(byte[] manifestJson) throws IOException {
        if (manifestJson == null) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(manifestJson);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to hash the document manifest", e);
        }
    }

    /** {@code b64url(SHA-256(SubjectPublicKeyInfo DER))}, the clinic key ID myCarlos pins. */
    static String keyId(X509Certificate certificate) throws IOException {
        try {
            byte[] spki = certificate.getPublicKey().getEncoded();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(spki));
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to compute the signing key ID", e);
        }
    }

    /**
     * Adds the manifest as an embedded file, replacing an earlier one of the same name, and keeps
     * every other embedded file. A name tree split into kids is flattened into one leaf.
     */
    private static void embedManifest(PDDocument document, byte[] json) throws IOException {
        PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(json));
        embedded.setSubtype("application/json");
        embedded.setSize(json.length);
        PDComplexFileSpecification specification = new PDComplexFileSpecification();
        specification.setFile(DocumentManifest.FILE_NAME);
        specification.setFileUnicode(DocumentManifest.FILE_NAME);
        specification.setEmbeddedFile(embedded);
        specification.setEmbeddedFileUnicode(embedded);
        specification.setFileDescription("CARLOS document manifest");
        specification.getCOSObject().setItem(COSName.AF_RELATIONSHIP, COSName.getPDFName("Data"));

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDDocumentNameDictionary names = catalog.getNames();
        if (names == null) {
            names = new PDDocumentNameDictionary(catalog);
            catalog.setNames(names);
        }
        Map<String, PDComplexFileSpecification> entries = new TreeMap<>();
        PDEmbeddedFilesNameTreeNode existing = names.getEmbeddedFiles();
        if (existing != null) {
            collectEmbeddedFiles(existing, entries);
        }
        entries.put(DocumentManifest.FILE_NAME, specification);
        PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
        tree.setNames(entries);
        names.setEmbeddedFiles(tree);

        COSArray associatedFiles = new COSArray();
        COSArray previous = catalog.getCOSObject().getCOSArray(COSName.AF);
        if (previous != null) {
            for (int i = 0; i < previous.size(); i++) {
                COSBase entry = previous.getObject(i);
                boolean earlierManifest = entry instanceof COSDictionary dictionary
                        && DocumentManifest.FILE_NAME.equals(dictionary.getString(COSName.F));
                if (!earlierManifest) {
                    associatedFiles.add(previous.get(i));
                }
            }
        }
        associatedFiles.add(specification);
        catalog.getCOSObject().setItem(COSName.AF, associatedFiles);
    }

    private static void collectEmbeddedFiles(PDNameTreeNode<PDComplexFileSpecification> node,
            Map<String, PDComplexFileSpecification> entries) throws IOException {
        Map<String, PDComplexFileSpecification> leaf = node.getNames();
        if (leaf != null) {
            entries.putAll(leaf);
        }
        List<PDNameTreeNode<PDComplexFileSpecification>> kids = node.getKids();
        if (kids != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> kid : new ArrayList<>(kids)) {
                collectEmbeddedFiles(kid, entries);
            }
        }
    }

    /**
     * Opens the PDF without a password first, and only then with the supplied one.
     *
     * <p>The caller's password belongs to the send, not to this file: an email can carry an
     * encrypted message PDF alongside attachments CARLOS never encrypted. Offering the send
     * password to an uploaded PDF that a third party restricted with its own owner password is
     * rejected outright, although that same file opens, and signs, with no password at all.</p>
     */
    private static PDDocument loadPDF(Path pdfPath, String ownerPassword) throws IOException {
        File pdfFile = PathValidationUtils.resolveTrustedPath(pdfPath.toFile());
        try {
            return Loader.loadPDF(pdfFile);
        } catch (InvalidPasswordException passwordRequired) {
            if (ownerPassword == null || ownerPassword.isEmpty()) {
                throw passwordRequired;
            }
            return Loader.loadPDF(pdfFile, ownerPassword);
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: keystore path is a server-side configuration value validated as an existing file before use.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "keystore path is server-side configuration validated with PathValidationUtils.validateConfiguredFile")
    private static SigningMaterial loadSigningMaterial(PDFSigningConfig config) throws IOException {
        File keystoreFile = PathValidationUtils.validateConfiguredFile(config.getKeystorePath(), "PDF signing keystore");
        char[] keystorePassword = null;
        char[] keyPassword = null;
        try (InputStream input = Files.newInputStream(keystoreFile.toPath())) {
            KeyStore keyStore = KeyStore.getInstance(config.getKeystoreType());
            keystorePassword = config.getKeystorePassword();
            keyPassword = config.getKeyPassword();
            keyStore.load(input, keystorePassword);

            String alias = config.getKeyAlias();
            if (!keyStore.isKeyEntry(alias)) {
                throw new IOException("PDF signing key alias does not contain a private key");
            }
            Key key = keyStore.getKey(alias, keyPassword);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new IOException("PDF signing key alias did not resolve to a private key");
            }

            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (chain == null || chain.length == 0) {
                Certificate certificate = keyStore.getCertificate(alias);
                chain = certificate == null ? new Certificate[0] : new Certificate[]{certificate};
            }
            X509Certificate[] certificates = toX509CertificateChain(chain);
            validateSigningCertificate(privateKey, certificates[0]);
            return new SigningMaterial(privateKey, certificates);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Failed to load PDF signing key material", e);
        } finally {
            clearPassword(keystorePassword);
            clearPassword(keyPassword);
        }
    }

    private static X509Certificate[] toX509CertificateChain(Certificate[] chain) throws IOException {
        if (chain.length == 0) {
            throw new IOException("PDF signing key alias does not have a certificate chain");
        }

        X509Certificate[] certificates = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate x509Certificate)) {
                throw new IOException("PDF signing certificate chain must contain X.509 certificates");
            }
            certificates[i] = x509Certificate;
        }
        return certificates;
    }

    private static void validateSigningCertificate(PrivateKey privateKey, X509Certificate certificate)
            throws GeneralSecurityException {
        certificate.checkValidity();
        validateKeyUsage(certificate);

        byte[] challenge = "CARLOS PDF signing certificate validation".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(signatureAlgorithm(privateKey));
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signedChallenge = signer.sign();

        signer.initVerify(certificate.getPublicKey());
        signer.update(challenge);
        if (!signer.verify(signedChallenge)) {
            throw new SignatureException("PDF signing private key does not match certificate");
        }
    }

    private static void validateKeyUsage(X509Certificate certificate) throws CertificateException {
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null) {
            return;
        }

        boolean digitalSignature = keyUsage.length > 0 && keyUsage[0];
        boolean contentCommitment = keyUsage.length > 1 && keyUsage[1];
        if (!digitalSignature && !contentCommitment) {
            throw new CertificateException("PDF signing certificate is not valid for digital signatures");
        }
    }

    private static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static void deletePartialSignedPDF(Path signedPDFPath, Exception signingFailure) {
        try {
            Files.deleteIfExists(signedPDFPath);
        } catch (IOException deleteFailure) {
            signingFailure.addSuppressed(deleteFailure);
        }
    }

    private static void ensureBouncyCastleProvider() {
        // Registering BouncyCastle is a process-wide, permanent JCE side effect: it mutates the JVM
        // security provider list for the whole application, not just this class. The presence check
        // keeps it idempotent so concurrent/repeat signing calls do not re-register the provider.
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // FindSecBugs IMPROPER_UNICODE: JCA algorithm names are ASCII identifiers ("RSA", "EC"); this is an intended case-insensitive match, not a trust decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "JCA algorithm names are ASCII identifiers; intended case-insensitive match, not a trust decision")
    private static String signatureAlgorithm(PrivateKey privateKey) {
        String keyAlgorithm = privateKey.getAlgorithm();
        if ("RSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withRSA";
        }
        if ("EC".equalsIgnoreCase(keyAlgorithm) || "ECDSA".equalsIgnoreCase(keyAlgorithm)) {
            return "SHA256withECDSA";
        }
        throw new IllegalArgumentException("Unsupported PDF signing key algorithm: " + keyAlgorithm);
    }

    @SuppressWarnings("java:S6206") // Mutable certificate chain array requires defensive copies.
    private static final class SigningMaterial {
        private final PrivateKey privateKey;
        private final X509Certificate[] certificateChain;

        private SigningMaterial(PrivateKey privateKey, X509Certificate[] certificateChain) {
            this.privateKey = privateKey;
            this.certificateChain = Arrays.copyOf(certificateChain, certificateChain.length);
        }

        private PrivateKey privateKey() {
            return privateKey;
        }

        private X509Certificate signingCertificate() {
            return certificateChain[0];
        }

        private X509Certificate[] certificateChain() {
            return Arrays.copyOf(certificateChain, certificateChain.length);
        }
    }

    private static final class CmsDetachedSignature implements SignatureInterface {
        private final SigningMaterial signingMaterial;
        private final byte[] manifestDigest;
        private int signatureLength = -1;

        /** @param manifestDigest SHA-256 of the embedded manifest, or {@code null} without one */
        private CmsDetachedSignature(SigningMaterial signingMaterial, byte[] manifestDigest) {
            this.signingMaterial = signingMaterial;
            this.manifestDigest = manifestDigest == null ? null : manifestDigest.clone();
        }

        /** The length of the CMS this signer produced, or -1 before it has signed. */
        int signatureLength() {
            return signatureLength;
        }

        @Override
        public byte[] sign(InputStream content) throws IOException {
            try {
                X509Certificate signingCertificate = signingMaterial.signingCertificate();
                ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm(signingMaterial.privateKey()))
                        .setProvider(PROVIDER_NAME)
                        .build(signingMaterial.privateKey());

                CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
                generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER_NAME).build())
                        .setSignedAttributeGenerator(new PadesSignedAttributes(signingCertificate, manifestDigest))
                        .build(signer, signingCertificate));
                generator.addCertificates(new JcaCertStore(Arrays.asList(signingMaterial.certificateChain())));

                CMSSignedData signedData = generator.generate(new InputStreamTypedData(content), false);
                // DER, as ISO 32000-1 12.8.3.3.1 requires. The default is indefinite-length BER,
                // which lenient readers accept and strict validators reject.
                byte[] encoded = signedData.getEncoded("DER");
                signatureLength = encoded.length;
                return encoded;
            } catch (CMSException | GeneralSecurityException | IllegalArgumentException | OperatorCreationException e) {
                throw new IOException("Failed to create detached PDF signature", e);
            }
        }
    }

    /**
     * The signed attributes of a PAdES baseline B-B signature: Bouncy Castle's standard set (content
     * type, message digest, algorithm protection) plus an ESS signing-certificate-v2 that binds the
     * signer certificate, without the CMS signing-time attribute that PAdES baseline excludes.
     *
     * <p>A signature over a document with an embedded manifest also carries
     * {@link #DOCUMENT_MANIFEST_SHA256}: one OCTET STRING holding SHA-256 of the exact manifest
     * bytes. It ties the manifest to the signature even when the PDF's own encryption hides the
     * embedded file, so myCarlos can check it without decrypting the PDF itself. Validators that
     * do not know the attribute ignore it; being signed, it cannot be added or changed later.</p>
     */
    private static final class PadesSignedAttributes implements CMSAttributeTableGenerator {
        private final DefaultSignedAttributeTableGenerator standard;

        private PadesSignedAttributes(X509Certificate signingCertificate, byte[] manifestDigest)
                throws GeneralSecurityException {
            byte[] certificateHash = MessageDigest.getInstance("SHA-256").digest(signingCertificate.getEncoded());
            IssuerSerial issuerSerial = new IssuerSerial(
                    new GeneralNames(new GeneralName(X500Name.getInstance(signingCertificate.getIssuerX500Principal().getEncoded()))),
                    signingCertificate.getSerialNumber());
            // SHA-256 is ESSCertIDv2's default hash algorithm, so it is left out of the encoding.
            SigningCertificateV2 signingCertificateV2 =
                    new SigningCertificateV2(new ESSCertIDv2[]{new ESSCertIDv2(certificateHash, issuerSerial)});
            ASN1EncodableVector extra = new ASN1EncodableVector();
            extra.add(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(signingCertificateV2)));
            if (manifestDigest != null) {
                extra.add(new Attribute(DOCUMENT_MANIFEST_SHA256, new DERSet(new DEROctetString(manifestDigest))));
            }
            this.standard = new DefaultSignedAttributeTableGenerator(new AttributeTable(extra));
        }

        @Override
        @SuppressWarnings("rawtypes") // Bouncy Castle's interface takes a raw Map.
        public AttributeTable getAttributes(Map parameters) {
            return standard.getAttributes(parameters).remove(CMSAttributes.signingTime);
        }
    }

    private static final class InputStreamTypedData implements CMSTypedData {
        private final InputStream inputStream;

        private InputStreamTypedData(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public ASN1ObjectIdentifier getContentType() {
            return PKCSObjectIdentifiers.data;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException, CMSException {
            inputStream.transferTo(outputStream);
        }

        @Override
        public Object getContent() {
            return inputStream;
        }
    }
}
