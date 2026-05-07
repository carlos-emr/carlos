package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
/**
 * Provides utility methods for securing and encrypting PDF documents.
 * <p>
 * Includes functionality for applying password protection and cryptographic constraints
 * to ensure privacy and compliance when handling generated PDF files.
 *
 * @since 2026-05-05
 */

public class PDFEncryptionUtil {
    public static Path encryptPDF(Path pdfPath, String password) throws IOException {
        try (PDDocument pdDocument = Loader.loadPDF(pdfPath.toFile())) {
            StandardProtectionPolicy spp = new StandardProtectionPolicy(password, password, new AccessPermission());
            spp.setEncryptionKeyLength(256);
            pdDocument.protect(spp);
            Path encryptPDFPath = Files.createTempFile("encryptedPDF_" + System.currentTimeMillis(), ".pdf");
            pdDocument.save(encryptPDFPath.toFile());
            return encryptPDFPath;
        } catch (IOException e) {
            throw new IOException("Failed to encrypt document", e);
        }
    }
}
