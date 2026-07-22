package io.github.carlos_emr.carlos.utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Applies certificate-backed detached signatures to PDF files.
 */
public final class PDFSigningUtil {
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final int PREFERRED_SIGNATURE_SIZE = 32768;

    private PDFSigningUtil() {
    }

    public static Path signPDF(Path pdfPath, PDFSigningConfig config) throws IOException {
        return signPDF(pdfPath, config, null);
    }

    public static Path signPDF(Path pdfPath, PDFSigningConfig config, String ownerPassword) throws IOException {
        if (pdfPath == null) {
            throw new IOException("PDF path is required for signing");
        }
        if (config == null || !config.isEnabled()) {
            return pdfPath;
        }

        config.validateEnabled();
        ensureBouncyCastleProvider();

        SigningMaterial signingMaterial = loadSigningMaterial(config);
        Path signedPDFPath = PathValidationUtils.createSecureTempFile(
                PathValidationUtils.validateGeneratedFileName("signedPDF_" + System.currentTimeMillis()), ".pdf").toPath();

        try (PDDocument document = loadPDF(pdfPath, ownerPassword);
             SignatureOptions signatureOptions = new SignatureOptions();
             OutputStream output = Files.newOutputStream(signedPDFPath)) {
            signatureOptions.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(config.getSignerName());
            signature.setReason(config.getReason());
            if (config.getLocation() != null) {
                signature.setLocation(config.getLocation());
            }
            if (config.getContact() != null) {
                signature.setContactInfo(config.getContact());
            }
            signature.setSignDate(Calendar.getInstance());

            document.addSignature(signature, new CmsDetachedSignature(signingMaterial), signatureOptions);
            document.saveIncremental(output);
            return signedPDFPath;
        } catch (IOException e) {
            Files.deleteIfExists(signedPDFPath);
            throw new IOException("Failed to sign PDF document", e);
        }
    }

    private static PDDocument loadPDF(Path pdfPath, String ownerPassword) throws IOException {
        File pdfFile = PathValidationUtils.resolveTrustedPath(pdfPath.toFile());
        if (ownerPassword == null || ownerPassword.isEmpty()) {
            return Loader.loadPDF(pdfFile);
        }
        return Loader.loadPDF(pdfFile, ownerPassword);
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
            return new SigningMaterial(privateKey, certificates);
        } catch (GeneralSecurityException e) {
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

    private static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private record SigningMaterial(PrivateKey privateKey, X509Certificate[] certificateChain) {
    }

    private static final class CmsDetachedSignature implements SignatureInterface {
        private final SigningMaterial signingMaterial;

        private CmsDetachedSignature(SigningMaterial signingMaterial) {
            this.signingMaterial = signingMaterial;
        }

        @Override
        public byte[] sign(InputStream content) throws IOException {
            try {
                X509Certificate signingCertificate = signingMaterial.certificateChain()[0];
                ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm(signingMaterial.privateKey()))
                        .setProvider(PROVIDER_NAME)
                        .build(signingMaterial.privateKey());

                CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
                generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER_NAME).build())
                        .build(signer, signingCertificate));
                generator.addCertificates(new JcaCertStore(Arrays.asList(signingMaterial.certificateChain())));

                CMSSignedData signedData = generator.generate(new InputStreamTypedData(content), false);
                return signedData.getEncoded();
            } catch (CMSException | GeneralSecurityException | IllegalArgumentException | OperatorCreationException e) {
                throw new IOException("Failed to create detached PDF signature", e);
            }
        }

        private static String signatureAlgorithm(PrivateKey privateKey) {
            String keyAlgorithm = privateKey.getAlgorithm().toUpperCase(Locale.ROOT);
            return switch (keyAlgorithm) {
                case "RSA" -> "SHA256withRSA";
                case "EC", "ECDSA" -> "SHA256withECDSA";
                default -> throw new IllegalArgumentException("Unsupported PDF signing key algorithm: " + keyAlgorithm);
            };
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
