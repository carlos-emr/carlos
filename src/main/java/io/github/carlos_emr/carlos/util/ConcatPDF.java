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

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ConcatPDF {


    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static void concat(ArrayList<Object> alist, String filename) {
        File outputFile = PathValidationUtils.resolveTrustedPath(new File(filename));
        try (OutputStream os = new FileOutputStream(outputFile)) {
            concat(alist, os);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to concatenate {} PDF documents to file: {}", alist.size(), filename, e);
        }
    }

    /**
     * Concatenates the given PDF inputs (file paths or InputStreams) into {@code outputStream}.
     * Inputs that cannot be opened/read are skipped rather than aborting the whole merge.
     * (This was an example known as PdfCopy.java)
     *
     * @return the number of inputs that were skipped; {@code 0} means every input was included
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public static int concat(List<Object> fileOrInputStreamPdfList, OutputStream outputStream) {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        PDDocument documentReader;
        int totalFiles = fileOrInputStreamPdfList.size();
        int skippedFiles = 0;

        for (Object o : fileOrInputStreamPdfList) {
            //load pdf file
            try {
                if (o instanceof InputStream) {
                    documentReader = Loader.loadPDF(((InputStream) o).readAllBytes());
                } else {
                    Path fileName = PathValidationUtils.resolveTrustedPath(new File((String) o)).toPath();
                    documentReader = Loader.loadPDF(fileName.toFile());
                }

                // remove encryption
                if (documentReader != null && documentReader.isEncrypted()) {
                    documentReader.setAllSecurityToBeRemoved(true);
                }
            } catch (IOException | SecurityException e) {
                // SecurityException covers PathValidationUtils rejecting a malformed entry path; skip that
                // entry and continue merging the rest rather than aborting the whole concatenation.
                skippedFiles++;
                MiscUtils.getLogger().error("Failed to open file for concatenation: " + o, e);
                continue;
            }

            // save document to output stream and add resulting data to merger
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (documentReader != null) {
                    documentReader.save(baos);
                    try (InputStream inputStream = new ByteArrayInputStream(baos.toByteArray())) {
                        pdfMerger.addSource(new RandomAccessReadBuffer(inputStream));
                        documentReader.close();
                    }
                }
            } catch (IOException e) {
                skippedFiles++;
                MiscUtils.getLogger().error("Document could not be added to merge " + o, e);
            }
        }

        if (skippedFiles > 0) {
            MiscUtils.getLogger().error("PDF merge: {} of {} documents could not be included in the merged output",
                    skippedFiles, totalFiles);
        }

        try {
            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(null);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Document merge failed.", e);
            throw new RuntimeException("PDF merge failed after processing " + totalFiles + " documents", e);
        }

        // Return how many inputs were skipped (bad path / unreadable / corrupt) so direct-response
        // callers can surface "N document(s) could not be included" instead of a silently truncated PDF.
        return skippedFiles;
    }

}
