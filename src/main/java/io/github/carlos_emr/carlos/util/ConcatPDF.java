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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class ConcatPDF {


    public static void concat(ArrayList<Object> alist, String filename) {
        try (OutputStream os = new FileOutputStream(filename);) {
            concat(alist, os);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    /**
     * This class can be used to concatenate existing PDF files.
     * (This was an example known as PdfCopy.java)
     */
    public static void concat(List<Object> fileOrInputStreamPdfList, OutputStream outputStream) {
        PDFMergerUtility pdfMerger = new PDFMergerUtility();
        PDDocument documentReader;
        for (Object o : fileOrInputStreamPdfList) {
            //load pdf file
            try {
                if (o instanceof InputStream) {
                    documentReader = PDDocument.load((InputStream) o);
                } else {
                    Path fileName = Paths.get((String) o);
                    documentReader = PDDocument.load(fileName.toFile());
                }

                // remove encryption
                if (documentReader != null && documentReader.isEncrypted()) {
                    documentReader.setAllSecurityToBeRemoved(true);
                }
            } catch (IOException e) {
                MiscUtils.getLogger().error("Failed to open file for concatenation: " + o, e);
                continue;
            }

            // save document to output stream and add resulting data to merger
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (documentReader != null) {
                    documentReader.save(baos);
                    try (InputStream inputStream = new ByteArrayInputStream(baos.toByteArray())) {
                        pdfMerger.addSource(inputStream);
                        documentReader.close();
                    }
                }
            } catch (IOException e) {
                MiscUtils.getLogger().error("Document could not be added to merge " + o, e);
            }
        }

        try {
            pdfMerger.setDestinationStream(outputStream);
            pdfMerger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Document merge failed.", e);
        }
    }

}
