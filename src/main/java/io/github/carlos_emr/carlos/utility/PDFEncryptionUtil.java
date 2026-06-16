package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Utility class providing helper methods for PDFEncryptionUtil operations.
 * Isolates complex format transformations and standardizes reusable operations across different application layers.
 */
public class PDFEncryptionUtil {
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static Path encryptPDF(Path pdfPath, String password) throws IOException {
        try (PDDocument pdDocument = Loader.loadPDF(pdfPath.toFile())) {
            StandardProtectionPolicy spp = new StandardProtectionPolicy(password, password, new AccessPermission());
            // Enforces modern AES-256 encryption standards instead of older, insecure algorithms (like RC4 or AES-128) to comply with healthcare data protection requirements.
            spp.setEncryptionKeyLength(256);
            pdDocument.protect(spp);
            // createSecureTempFile applies OWNER-only permissions, so the encrypted PDF is not left
            // in the world-readable system temp directory with default perms (Sonar java:S5443).
            Path encryptPDFPath = PathValidationUtils.createSecureTempFile(PathValidationUtils.validateGeneratedFileName("encryptedPDF_" + System.currentTimeMillis()), ".pdf").toPath();
            pdDocument.save(encryptPDFPath.toFile());
            return encryptPDFPath;
        } catch (IOException e) {
            throw new IOException("Failed to encrypt document", e);
        }
    }
}
