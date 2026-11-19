/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.util;

/*
 *
 * This code is free software. It may only be copied or modified
 * if you include the following copyright notice:
 *
 * This class by Mark Thompson. Copyright (c) 2002 Mark Thompson.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * itext@lowagie.com
 */


/**
 * Concatenates multiple PDF files into a single output document using Apache PDFBox.
 *
 * @author Mark Thompson
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ConcatPDF {

    /**
     * Writes the merged PDF to a file and propagates failures so the caller can discard the output.
     *
     * @throws IOException if the destination cannot be opened or closed
     */
    public static void concat(ArrayList<Object> inputs, String filename) throws IOException {
        concatToFile(inputs, filename, false);
    }

    /**
     * Writes a complete PDF to a file. An unreadable input aborts the merge.
     *
     * @throws IOException if the destination cannot be opened or closed
     */
    public static void concatRequired(List<Object> inputs, String filename) throws IOException {
        concatToFile(inputs, filename, true);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "The destination uses the existing trusted-path validation")
    private static void concatToFile(List<Object> inputs, String filename, boolean requireAll) throws IOException {
        File outputFile = PathValidationUtils.resolveTrustedPath(new File(filename));
        try (OutputStream output = new FileOutputStream(outputFile)) {
            concat(inputs, output, requireAll);
        }
    }

    /**
     * Concatenates the given PDF inputs (file paths or InputStreams) into {@code outputStream}.
     * Inputs that cannot be opened/read are skipped rather than aborting the whole merge.
     * Failures preparing a merge-owned staging file abort before the destination is written.
     * (This was an example known as PdfCopy.java)
     *
     * @return the number of inputs that were skipped; {@code 0} means every input was included
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static int concat(List<Object> fileOrInputStreamPdfList, OutputStream outputStream) {
        return concat(fileOrInputStreamPdfList, outputStream, false);
    }

    /**
     * Merges required clinical sections. Any unreadable input fails before writing the destination,
     * so a chart or lab report cannot look complete while omitting a requested attachment.
     */
    public static void concatRequired(List<Object> inputs, OutputStream outputStream) {
        concat(inputs, outputStream, true);
    }

    private static int concat(List<Object> fileOrInputStreamPdfList, OutputStream outputStream,
                              boolean requireAll) {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        List<File> stagedSources = new ArrayList<>();
        int totalFiles = fileOrInputStreamPdfList.size();
        int skippedFiles = 0;

        try {
            for (Object input : fileOrInputStreamPdfList) {
                File staged = PathValidationUtils.createSecureTempFile("pdf-merge-source-", ".pdf");
                stagedSources.add(staged);
                if (stageSource(input, staged)) {
                    pdfMerger.addSource(staged);
                } else {
                    skippedFiles++;
                }
            }

            if (skippedFiles > 0) {
                MiscUtils.getLogger().error("PDF merge: {} of {} documents could not be included in the merged output",
                        skippedFiles, totalFiles);
            }
            if (requireAll && skippedFiles > 0) {
                throw new IllegalStateException("Required PDF inputs could not be included");
            }

            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException e) {
            MiscUtils.getLogger().error("Document merge failed ({})", e.getClass().getSimpleName());
            throw new RuntimeException("PDF merge failed after processing " + totalFiles + " documents");
        } finally {
            for (File staged : stagedSources) {
                deleteStagedSource(staged.toPath());
            }
        }

        return skippedFiles;
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "file inputs use the existing trusted-path validation")
    private static boolean stageSource(Object input, File staged) throws IOException {
        if (input instanceof InputStream stream) {
            File source = PathValidationUtils.createSecureTempFile("pdf-merge-input-", ".pdf");
            try {
                return spoolSource(stream, source.toPath()) && normalizeSource(source, staged);
            } finally {
                deleteStagedSource(source.toPath());
            }
        }
        File source;
        try {
            source = PathValidationUtils.resolveTrustedPath(new File((String) input));
        } catch (SecurityException refused) {
            return skipUnreadableSource(refused);
        }
        return normalizeSource(source, staged);
    }

    private static boolean spoolSource(InputStream stream, Path source) throws IOException {
        byte[] buffer = new byte[8192];
        try (OutputStream spool = Files.newOutputStream(source)) {
            while (true) {
                int read;
                try {
                    read = stream.read(buffer);
                } catch (IOException | SecurityException unreadable) {
                    return skipUnreadableSource(unreadable);
                }
                if (read < 0) return true;
                spool.write(buffer, 0, read);
            }
        }
    }

    private static boolean normalizeSource(File source, File staged) throws IOException {
        PDDocument document;
        try {
            document = Loader.loadPDF(source, IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException | SecurityException unreadable) {
            return skipUnreadableSource(unreadable);
        }
        try (document) {
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
            }
            document.save(staged);
        }
        return true;
    }

    private static boolean skipUnreadableSource(Exception unreadable) {
        MiscUtils.getLogger().error("PDF input could not be added to merge ({})", unreadable.getClass().getSimpleName());
        return false;
    }

    private static void deleteStagedSource(Path staged) {
        try {
            Files.deleteIfExists(staged);
        } catch (IOException | SecurityException failure) {
            MiscUtils.getLogger().warn("Could not delete a temporary PDF merge source ({})",
                    failure.getClass().getSimpleName());
        }
    }

}
