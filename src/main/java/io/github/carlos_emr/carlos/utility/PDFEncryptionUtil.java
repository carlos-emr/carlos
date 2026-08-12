package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Provides reusable utility functions and helper methods for PDFEncryptionUtil operations across the application.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public class PDFEncryptionUtil {
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static Path encryptPDF(Path pdfPath, String password) throws IOException {
        // Internal logic boundary for PDFEncryptionUtil state management
        try (PDDocument pdDocument = Loader.loadPDF(pdfPath.toFile())) {
            StandardProtectionPolicy spp = new StandardProtectionPolicy(password, password, new AccessPermission());
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
