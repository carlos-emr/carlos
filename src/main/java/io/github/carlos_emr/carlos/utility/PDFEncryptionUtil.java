package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * Utility class for encrypting PDF documents with password protection using
 * AES-256 encryption via Apache PDFBox.
 *
 * @since 2026-03-17
 */
public class PDFEncryptionUtil {

    /**
     * Encrypts a PDF file with the specified password and saves to a temporary file.
     *
     * @param pdfPath  Path the source PDF file to encrypt
     * @param password String the password for both owner and user access
     * @return Path the path to the encrypted PDF temporary file
     * @throws IOException if the PDF cannot be read or the encrypted version cannot be saved
     */
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
